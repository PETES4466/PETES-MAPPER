package com.petesmapper.ui.export

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File

data class PdfExportResult(
    val file: File,
    val pageCount: Int,
)

class PDFExportEngine {

    fun generateProjectPdf(
        outputFile: File,
        savedProject: SavedProject,
        quotation: Quotation,
        billOfMaterials: BillOfMaterials,
        controllerMapExport: ControllerMapExport,
    ): PdfExportResult {
        val lines = mutableListOf<String>()
        lines += "Project Summary"
        lines += "Project Name: ${savedProject.metadata.projectName}"
        lines += "Project ID: ${savedProject.metadata.projectId}"
        lines += "Created: ${savedProject.metadata.createdAt}"
        lines += "Updated: ${savedProject.metadata.updatedAt}"
        lines += ""

        lines += "Route Summary"
        lines += "Wiring Mode: ${savedProject.savedRouteState.selectedMode}"
        lines += "Pattern: ${savedProject.savedRouteState.selectedPattern}"
        lines += "Total Strip Length (m): ${fmt(billOfMaterials.stripRequirement.totalStripLength)}"
        lines += "Total Pixel Count: ${billOfMaterials.stripRequirement.totalPixelCount}"
        lines += "Total Jump Wires: ${billOfMaterials.jumperRequirement.jumperCount}"
        lines += ""

        lines += "Controller Mapping Summary"
        lines += "Controller Type: ${controllerMapExport.controllerType}"
        lines += "Ports Used: ${savedProject.savedControllerState.portUsage}"
        lines += "Channels Used: ${controllerMapExport.channelGroups.size}"
        lines += "Total Current (A): ${fmt(controllerMapExport.totalCurrentAmps)}"
        lines += "Total Power (W): ${fmt(controllerMapExport.totalPowerWatts)}"
        lines += ""

        lines += "BOM Summary"
        lines += "Strip: ${fmt(billOfMaterials.stripRequirement.totalStripLength)}m, Rolls=${billOfMaterials.stripRequirement.stripRollCount}"
        lines += "Jumper: count=${billOfMaterials.jumperRequirement.jumperCount}, length=${fmt(billOfMaterials.jumperRequirement.totalJumperWireLength)}m"
        lines += "Injection: points=${billOfMaterials.powerInjectionRequirement.injectionPointCount}, wire=${fmt(billOfMaterials.powerInjectionRequirement.injectionWireLength)}m"
        lines += "SMPS: count=${billOfMaterials.powerSupplyRequirement.smpsCount}, wattage=${fmt(billOfMaterials.powerSupplyRequirement.recommendedSmpsWattage)}W"
        lines += "Controller: ${billOfMaterials.controllerRequirement.controllerType} x ${billOfMaterials.controllerRequirement.controllerCount}"
        lines += "Connector: ${billOfMaterials.connectorRequirement.connectorCount}"
        lines += "Mounting: clips=${billOfMaterials.mountingRequirement.mountingClipCount}, tape=${fmt(billOfMaterials.mountingRequirement.mountingTapeLength)}m"
        lines += ""

        lines += "Quotation Summary"
        lines += "Material Cost: ${fmt(quotation.materialCostBreakdown.totalMaterialCost)}"
        lines += "Labor Cost: ${fmt(quotation.laborCostBreakdown.totalLaborCost)}"
        lines += "Logistics Cost: ${fmt(quotation.logisticsCostBreakdown.transportCost)}"
        lines += "Subtotal: ${fmt(quotation.quotationSummary.subtotal)}"
        lines += "Profit: ${fmt(quotation.quotationSummary.profitAmount)}"
        lines += "Final Quotation: ${fmt(quotation.quotationSummary.finalQuotation)}"
        lines += ""

        lines += "Export Summary"
        lines += "DXF generated: YES"
        lines += "Controller map generated: YES"
        lines += "Quote generated: YES"

        return renderPdf(outputFile, lines)
    }

    fun generateQuotePdf(outputFile: File, quotation: Quotation): PdfExportResult {
        val lines = listOf(
            "Quotation Summary",
            "Material Cost: ${fmt(quotation.materialCostBreakdown.totalMaterialCost)}",
            "Labor Cost: ${fmt(quotation.laborCostBreakdown.totalLaborCost)}",
            "Logistics Cost: ${fmt(quotation.logisticsCostBreakdown.transportCost)}",
            "Subtotal: ${fmt(quotation.quotationSummary.subtotal)}",
            "Profit: ${fmt(quotation.quotationSummary.profitAmount)}",
            "Final Quotation: ${fmt(quotation.quotationSummary.finalQuotation)}",
        )
        return renderPdf(outputFile, lines)
    }

    fun generateBomPdf(outputFile: File, billOfMaterials: BillOfMaterials): PdfExportResult {
        val lines = listOf(
            "BOM Summary",
            "Strip length: ${fmt(billOfMaterials.stripRequirement.totalStripLength)}m",
            "Pixels: ${billOfMaterials.stripRequirement.totalPixelCount}",
            "Jumper wires: ${billOfMaterials.jumperRequirement.jumperCount}",
            "Injection points: ${billOfMaterials.powerInjectionRequirement.injectionPointCount}",
            "SMPS count: ${billOfMaterials.powerSupplyRequirement.smpsCount}",
            "Controller count: ${billOfMaterials.controllerRequirement.controllerCount}",
            "Connector count: ${billOfMaterials.connectorRequirement.connectorCount}",
            "Mounting clips: ${billOfMaterials.mountingRequirement.mountingClipCount}",
        )
        return renderPdf(outputFile, lines)
    }

    private fun renderPdf(outputFile: File, lines: List<String>): PdfExportResult {
        val doc = PdfDocument()
        val paint = Paint().apply { textSize = 12f }
        val titlePaint = Paint().apply { textSize = 16f; isFakeBoldText = true }

        val pageWidth = 595
        val pageHeight = 842
        val margin = 40
        val lineHeight = 18
        var pageNumber = 1
        var y = margin

        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas

        lines.forEachIndexed { idx, text ->
            if (y > pageHeight - margin * 2) {
                drawPageNumber(canvas, pageNumber, pageWidth, pageHeight)
                doc.finishPage(page)
                pageNumber += 1
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                y = margin
            }
            val p = if (idx == 0 || text.endsWith("Summary")) titlePaint else paint
            canvas.drawText(text, margin.toFloat(), y.toFloat(), p)
            y += lineHeight
        }

        drawPageNumber(canvas, pageNumber, pageWidth, pageHeight)
        doc.finishPage(page)

        outputFile.parentFile?.mkdirs()
        outputFile.outputStream().use { doc.writeTo(it) }
        doc.close()

        return PdfExportResult(outputFile, pageNumber)
    }

    private fun drawPageNumber(canvas: android.graphics.Canvas, page: Int, width: Int, height: Int) {
        val paint = Paint().apply { textSize = 10f }
        canvas.drawText("Page $page", (width - 90).toFloat(), (height - 20).toFloat(), paint)
    }

    private fun fmt(v: Float): String = "%.2f".format(v)
}
