package com.kanraeh.app.meds

import com.kanraeh.app.core.Clock
import com.kanraeh.app.core.EventDao
import com.kanraeh.app.core.EventTypes
import com.kanraeh.app.core.LogEvent
import com.kanraeh.app.core.Time

/** MODULE: meds - medicines, treatments, lab results, and painkiller-day counting (medication-overuse warning). */
enum class MedClass(val code: String, val label: String, val monthlyLimitDays: Int?) {
    // Limits follow the ICHD-3 medication-overuse thresholds; verify against your knowledge base review.
    TRIPTAN("triptan", "Triptan", 10),
    COMBINATION("combo", "Combination painkiller", 10),
    OPIOID("opioid", "Opioid painkiller", 10),
    NSAID("nsaid", "Anti-inflammatory (ibuprofen etc.)", 15),
    PARACETAMOL("paracetamol", "Paracetamol", 15),
    ANTI_NAUSEA("anti_nausea", "Anti-nausea", null),
    PREVENTIVE("preventive", "Preventive migraine medicine", null),
    HORMONAL("hormonal", "Hormonal pill / treatment", null),
    PCOS_TREATMENT("pcos", "PCOS treatment (metformin etc.)", null),
    SUPPLEMENT("supplement", "Supplement", null);

    companion object { fun from(code: String?) = values().firstOrNull { it.code == code } }
}

enum class OveruseStatus { OK, WATCH, OVER }
data class OveruseLine(val med: MedClass, val daysLast30: Int, val limit: Int, val status: OveruseStatus)

class MedicineLogger(private val dao: EventDao) {
    suspend fun log(name: String, cls: MedClass, dose: String = "") {
        dao.insert(LogEvent(type = EventTypes.MED, key = cls.code, text = "$name|$dose", ts = Clock.now()))
    }

    suspend fun logLab(name: String, value: Double, unit: String) {
        dao.insert(LogEvent(type = EventTypes.LAB, key = name, value = value, text = unit, ts = Clock.now()))
    }

    suspend fun overuse(now: Long = Clock.now()): List<OveruseLine> {
        val meds = dao.typeBetween(EventTypes.MED, now - 30 * Time.DAY, now)
        return MedClass.values().mapNotNull { cls ->
            val limit = cls.monthlyLimitDays ?: return@mapNotNull null
            val days = meds.filter { it.key == cls.code }.map { Time.startOfDay(it.ts) }.distinct().size
            if (days == 0) return@mapNotNull null
            val st = when { days >= limit -> OveruseStatus.OVER; days >= limit - 3 -> OveruseStatus.WATCH; else -> OveruseStatus.OK }
            OveruseLine(cls, days, limit, st)
        }
    }
}
