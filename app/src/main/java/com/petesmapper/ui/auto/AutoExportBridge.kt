package com.petesmapper.ui.auto

import com.petesmapper.ui.export.BillOfMaterials
import com.petesmapper.ui.export.BOMGenerator
import com.petesmapper.ui.export.ControllerMapExport
import com.petesmapper.ui.export.ControllerMapExportEngine
import com.petesmapper.ui.export.ControllerType
import com.petesmapper.ui.export.DxfExportEngine
import com.petesmapper.ui.export.DxfExportRequest
import com.petesmapper.ui.export.LetterGeometry
import com.petesmapper.ui.export.PDFExportEngine
import com.petesmapper.ui.export.PdfExportResult
import com.petesmapper.ui.export.PixelVoltage
import com.petesmapper.ui.export.PricingInput
import com.petesmapper.ui.export.ProjectPersistenceEngine
import com.petesmapper.ui.export.QuoteGenerator
import com.petesmapper.ui.export.Quotation
import com.petesmapper.ui.export.SavedBomState
import com.petesmapper.ui.export.SavedControllerState
import com.petesmapper.ui.export.SavedProject
import com.petesmapper.ui.export.SavedProjectMetadata
import com.petesmapper.ui.export.SavedQuoteState
import com.petesmapper.ui.export.SavedRouteState
import com.petesmapper.ui.geometry.RoutePlan
import com.petesmapper.ui.geometry.Vec2
import com.petesmapper.ui.viewmodel.ProjectUiState
import java.io.File
import java.time.Instant
import java.util.UUID

data class AutoExportBundle(
    val dxf: String,
    val controllerMapExport: ControllerMapExport,
    val billOfMaterials: BillOfMaterials,
    val quotation: Quotation,
    val pdf: PdfExportResult,
)

class AutoExportBridge(
    private val dxfExportEngine: DxfExportEngine = DxfExportEngine(),
    private val controllerMapExportEngine: ControllerMapExportEngine = ControllerMapExportEngine(),
    private val bomGenerator: BOMGenerator = BOMGenerator(),
    private val quoteGenerator: QuoteGenerator = QuoteGenerator(),
    private val pdfExportEngine: PDFExportEngine = PDFExportEngine(),
) {

    fun exportDxf(routePlan: RoutePlan, projectUiState: ProjectUiState): String {
        val geometry = LetterGeometry(
            contours = projectUiState.letterGeometry.contours.map { contour -> contour.map { Vec2(it.first, it.second) } },
        )
        val controllerType = resolveControllerType(projectUiState.controllerType)
        return dxfExportEngine.export(
            DxfExportRequest(
                routePlan = routePlan,
                letterGeometry = geometry,
                controllerType = controllerType,
            ),
        )
    }

    fun exportControllerMap(routePlan: RoutePlan, projectUiState: ProjectUiState): ControllerMapExport {
        return controllerMapExportEngine.buildExport(
            routePlan = routePlan,
            controllerType = resolveControllerType(projectUiState.controllerType),
            pixelVoltage = resolveVoltage(projectUiState.voltageType),
            pixelCurrent = inferPixelCurrent(projectUiState),
            pixelSpacing = projectUiState.pixelSpacing.coerceAtLeast(0.01f),
        )
    }

    fun exportBom(
        routePlan: RoutePlan,
        controllerMapExport: ControllerMapExport,
        projectUiState: ProjectUiState,
    ): BillOfMaterials {
        return bomGenerator.generate(
            routePlan = routePlan,
            controllerMapExport = controllerMapExport,
            pixelVoltage = resolveVoltage(projectUiState.voltageType),
            pixelCurrent = inferPixelCurrent(projectUiState),
            pixelSpacing = projectUiState.pixelSpacing.coerceAtLeast(0.01f),
            controllerType = resolveControllerType(projectUiState.controllerType),
        )
    }

    fun exportQuote(billOfMaterials: BillOfMaterials, pricingInput: PricingInput): Quotation {
        return quoteGenerator.generate(bom = billOfMaterials, pricing = pricingInput)
    }

    fun exportPdf(
        outputFile: File,
        routePlan: RoutePlan,
        projectUiState: ProjectUiState,
        controllerMapExport: ControllerMapExport,
        billOfMaterials: BillOfMaterials,
        quotation: Quotation,
        pricingInput: PricingInput,
    ): PdfExportResult {
        val now = Instant.now().toString()
        val savedProject = SavedProject(
            metadata = SavedProjectMetadata(
                projectId = UUID.randomUUID().toString(),
                projectName = projectUiState.projectName.ifBlank { "AUTO Project" },
                createdAt = now,
                updatedAt = now,
                version = ProjectPersistenceEngine.CURRENT_SCHEMA_VERSION,
            ),
            projectUiState = projectUiState,
            savedRouteState = SavedRouteState(
                routePlan = routePlan,
                selectedMode = "AUTO",
                selectedPattern = "AUTO_REVERSE_ROUTE",
                lockedSegments = emptySet(),
                jumpConnections = routePlan.jumpConnections,
                manualModified = false,
            ),
            savedControllerState = SavedControllerState(
                controllerMapExport = controllerMapExport,
                controllerType = resolveControllerType(projectUiState.controllerType),
                portUsage = billOfMaterials.controllerRequirement.portUsage,
            ),
            savedBomState = SavedBomState(billOfMaterials),
            savedQuoteState = SavedQuoteState(quotation, pricingInput),
        )

        return pdfExportEngine.generateProjectPdf(
            outputFile = outputFile,
            savedProject = savedProject,
            quotation = quotation,
            billOfMaterials = billOfMaterials,
            controllerMapExport = controllerMapExport,
        )
    }

    fun exportAll(
        outputPdfFile: File,
        routePlan: RoutePlan,
        projectUiState: ProjectUiState,
        pricingInput: PricingInput,
    ): AutoExportBundle {
        val dxf = exportDxf(routePlan, projectUiState)
        val controllerMap = exportControllerMap(routePlan, projectUiState)
        val bom = exportBom(routePlan, controllerMap, projectUiState)
        val quote = exportQuote(bom, pricingInput)
        val pdf = exportPdf(outputPdfFile, routePlan, projectUiState, controllerMap, bom, quote, pricingInput)

        return AutoExportBundle(
            dxf = dxf,
            controllerMapExport = controllerMap,
            billOfMaterials = bom,
            quotation = quote,
            pdf = pdf,
        )
    }

    private fun resolveControllerType(value: String): ControllerType {
        return if (value.contains("8000", ignoreCase = true)) ControllerType.T8000 else ControllerType.T1000
    }

    private fun resolveVoltage(value: String): PixelVoltage = when {
        value.contains("24", true) -> PixelVoltage.V24
        value.contains("12", true) -> PixelVoltage.V12
        else -> PixelVoltage.V5
    }

    private fun inferPixelCurrent(projectUiState: ProjectUiState): Float {
        return when {
            projectUiState.voltageType.contains("24", true) -> 0.04f
            projectUiState.voltageType.contains("12", true) -> 0.06f
            else -> 0.08f
        }
    }
}
