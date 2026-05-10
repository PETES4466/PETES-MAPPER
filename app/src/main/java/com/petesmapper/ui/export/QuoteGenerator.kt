package com.petesmapper.ui.export

data class PricingInput(
    val stripCostPerMeter: Float,
    val jumperWireCostPerMeter: Float,
    val powerWireCostPerMeter: Float,
    val smpsCostPerUnit: Float,
    val controllerCostPerUnit: Float,
    val connectorCostPerUnit: Float,
    val mountingClipCostPerUnit: Float,
    val mountingTapeCostPerMeter: Float,
    val laborCost: Float,
    val designCost: Float,
    val installationCost: Float,
    val transportCost: Float,
    val profitMarginPercent: Float,
)

data class MaterialCostBreakdown(
    val stripCost: Float,
    val jumperWireCost: Float,
    val injectionWireCost: Float,
    val smpsCost: Float,
    val controllerCost: Float,
    val connectorCost: Float,
    val mountingCost: Float,
    val totalMaterialCost: Float,
)

data class LaborCostBreakdown(
    val laborCost: Float,
    val designCost: Float,
    val installationCost: Float,
    val totalLaborCost: Float,
)

data class LogisticsCostBreakdown(
    val transportCost: Float,
)

data class QuotationSummary(
    val subtotal: Float,
    val profitMarginPercent: Float,
    val profitAmount: Float,
    val finalQuotation: Float,
)

data class Quotation(
    val materialCostBreakdown: MaterialCostBreakdown,
    val laborCostBreakdown: LaborCostBreakdown,
    val logisticsCostBreakdown: LogisticsCostBreakdown,
    val quotationSummary: QuotationSummary,
    val pdfSummaryPlaceholder: String = "PDF summary integration pending",
) {
    fun exportAsJson(): String = """
        {
          "materialCostBreakdown": {
            "stripCost": ${fmt(materialCostBreakdown.stripCost)},
            "jumperWireCost": ${fmt(materialCostBreakdown.jumperWireCost)},
            "injectionWireCost": ${fmt(materialCostBreakdown.injectionWireCost)},
            "smpsCost": ${fmt(materialCostBreakdown.smpsCost)},
            "controllerCost": ${fmt(materialCostBreakdown.controllerCost)},
            "connectorCost": ${fmt(materialCostBreakdown.connectorCost)},
            "mountingCost": ${fmt(materialCostBreakdown.mountingCost)},
            "totalMaterialCost": ${fmt(materialCostBreakdown.totalMaterialCost)}
          },
          "laborCostBreakdown": {
            "laborCost": ${fmt(laborCostBreakdown.laborCost)},
            "designCost": ${fmt(laborCostBreakdown.designCost)},
            "installationCost": ${fmt(laborCostBreakdown.installationCost)},
            "totalLaborCost": ${fmt(laborCostBreakdown.totalLaborCost)}
          },
          "logisticsCostBreakdown": {
            "transportCost": ${fmt(logisticsCostBreakdown.transportCost)}
          },
          "quotationSummary": {
            "subtotal": ${fmt(quotationSummary.subtotal)},
            "profitMarginPercent": ${fmt(quotationSummary.profitMarginPercent)},
            "profitAmount": ${fmt(quotationSummary.profitAmount)},
            "finalQuotation": ${fmt(quotationSummary.finalQuotation)}
          },
          "pdfSummaryPlaceholder": "$pdfSummaryPlaceholder"
        }
    """.trimIndent()

    fun exportAsCsv(): String = buildString {
        appendLine("section,field,value")
        appendLine("material,stripCost,${fmt(materialCostBreakdown.stripCost)}")
        appendLine("material,jumperWireCost,${fmt(materialCostBreakdown.jumperWireCost)}")
        appendLine("material,injectionWireCost,${fmt(materialCostBreakdown.injectionWireCost)}")
        appendLine("material,smpsCost,${fmt(materialCostBreakdown.smpsCost)}")
        appendLine("material,controllerCost,${fmt(materialCostBreakdown.controllerCost)}")
        appendLine("material,connectorCost,${fmt(materialCostBreakdown.connectorCost)}")
        appendLine("material,mountingCost,${fmt(materialCostBreakdown.mountingCost)}")
        appendLine("material,totalMaterialCost,${fmt(materialCostBreakdown.totalMaterialCost)}")
        appendLine("labor,laborCost,${fmt(laborCostBreakdown.laborCost)}")
        appendLine("labor,designCost,${fmt(laborCostBreakdown.designCost)}")
        appendLine("labor,installationCost,${fmt(laborCostBreakdown.installationCost)}")
        appendLine("labor,totalLaborCost,${fmt(laborCostBreakdown.totalLaborCost)}")
        appendLine("logistics,transportCost,${fmt(logisticsCostBreakdown.transportCost)}")
        appendLine("summary,subtotal,${fmt(quotationSummary.subtotal)}")
        appendLine("summary,profitMarginPercent,${fmt(quotationSummary.profitMarginPercent)}")
        appendLine("summary,profitAmount,${fmt(quotationSummary.profitAmount)}")
        appendLine("summary,finalQuotation,${fmt(quotationSummary.finalQuotation)}")
    }

    fun exportAsTxt(): String = buildString {
        appendLine("QUOTATION")
        appendLine("Materials:")
        appendLine("  Strip: ${fmt(materialCostBreakdown.stripCost)}")
        appendLine("  Jumper Wire: ${fmt(materialCostBreakdown.jumperWireCost)}")
        appendLine("  Injection Wire: ${fmt(materialCostBreakdown.injectionWireCost)}")
        appendLine("  SMPS: ${fmt(materialCostBreakdown.smpsCost)}")
        appendLine("  Controller: ${fmt(materialCostBreakdown.controllerCost)}")
        appendLine("  Connector: ${fmt(materialCostBreakdown.connectorCost)}")
        appendLine("  Mounting: ${fmt(materialCostBreakdown.mountingCost)}")
        appendLine("Labor/Design/Install:")
        appendLine("  Labor: ${fmt(laborCostBreakdown.laborCost)}")
        appendLine("  Design: ${fmt(laborCostBreakdown.designCost)}")
        appendLine("  Installation: ${fmt(laborCostBreakdown.installationCost)}")
        appendLine("Logistics:")
        appendLine("  Transport: ${fmt(logisticsCostBreakdown.transportCost)}")
        appendLine("Summary:")
        appendLine("  Subtotal: ${fmt(quotationSummary.subtotal)}")
        appendLine("  Profit (${fmt(quotationSummary.profitMarginPercent)}%): ${fmt(quotationSummary.profitAmount)}")
        appendLine("  Final Quotation: ${fmt(quotationSummary.finalQuotation)}")
        appendLine("$pdfSummaryPlaceholder")
    }
}

class QuoteGenerator {
    fun generate(bom: BillOfMaterials, pricing: PricingInput): Quotation {
        require(pricing.profitMarginPercent >= 0f) { "profitMarginPercent must be >= 0" }

        val stripCost = bom.stripRequirement.totalStripLength * pricing.stripCostPerMeter
        val jumperWireCost = bom.jumperRequirement.totalJumperWireLength * pricing.jumperWireCostPerMeter
        val injectionWireCost = bom.powerInjectionRequirement.injectionWireLength * pricing.powerWireCostPerMeter
        val smpsCost = bom.powerSupplyRequirement.smpsCount * pricing.smpsCostPerUnit
        val controllerCost = bom.controllerRequirement.controllerCount * pricing.controllerCostPerUnit
        val connectorCost = bom.connectorRequirement.connectorCount * pricing.connectorCostPerUnit

        val mountingClipCost = bom.mountingRequirement.mountingClipCount * pricing.mountingClipCostPerUnit
        val mountingTapeCost = bom.mountingRequirement.mountingTapeLength * pricing.mountingTapeCostPerMeter
        val mountingCost = mountingClipCost + mountingTapeCost

        val totalMaterial = stripCost + jumperWireCost + injectionWireCost + smpsCost + controllerCost + connectorCost + mountingCost
        val totalLabor = pricing.laborCost + pricing.designCost + pricing.installationCost
        val transport = pricing.transportCost

        val subtotal = totalMaterial + totalLabor + transport
        val profitAmount = subtotal * (pricing.profitMarginPercent / 100f)
        val final = subtotal + profitAmount

        return Quotation(
            materialCostBreakdown = MaterialCostBreakdown(
                stripCost = stripCost,
                jumperWireCost = jumperWireCost,
                injectionWireCost = injectionWireCost,
                smpsCost = smpsCost,
                controllerCost = controllerCost,
                connectorCost = connectorCost,
                mountingCost = mountingCost,
                totalMaterialCost = totalMaterial,
            ),
            laborCostBreakdown = LaborCostBreakdown(
                laborCost = pricing.laborCost,
                designCost = pricing.designCost,
                installationCost = pricing.installationCost,
                totalLaborCost = totalLabor,
            ),
            logisticsCostBreakdown = LogisticsCostBreakdown(
                transportCost = transport,
            ),
            quotationSummary = QuotationSummary(
                subtotal = subtotal,
                profitMarginPercent = pricing.profitMarginPercent,
                profitAmount = profitAmount,
                finalQuotation = final,
            ),
        )
    }
}

private fun fmt(v: Float): String = "%.2f".format(v)
