package com.soniccore.app.repository

import com.soniccore.app.ui.navigation.SonicCoreCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract of the open-source repository inventory.
 *
 * The inventory is what the Repository Manager shows and what
 * `docs/repository-inventory.md` is generated from, so a claim it makes has to be
 * checkable. The most important assertion here is the compliance rule: a project
 * whose licence is copyleft, proprietary or unverified can never be reported as
 * integrated — or even as ready to integrate — into this MIT-licensed app.
 */
class RepositoryInventoryTest {

    private val all = RepositoryInventory.all

    // ── Every entry states its facts ──

    @Test
    fun everyEntryNamesItsSourceLicenceAndRealUse() {
        all.forEach { entry ->
            assertTrue("${entry.name} has no owner/name", entry.name.contains("/"))
            assertTrue("${entry.name} has no https source", entry.url.startsWith("https://"))
            assertTrue("${entry.name} states no licence", entry.license.isNotBlank())
            assertTrue("${entry.name} describes no upstream capability", entry.capability.isNotBlank())
            assertTrue("${entry.name} says nothing about SonicCore's use", entry.sonicCoreUse.isNotBlank())
            assertTrue("${entry.name} has no last-push date", Regex("\\d{4}-\\d{2}-\\d{2}").matches(entry.lastPushed))
            assertTrue("${entry.name} has a negative star count", entry.stars >= 0)
            assertTrue("${entry.name} has no api note", entry.apiNote.isNotBlank())
        }
    }

    @Test
    fun entryIdsAreUnique() {
        val ids = all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    // ── The compliance rule ──

    @Test
    fun nothingCopyleftProprietaryOrUnverifiedIsReportedAsIntegrated() {
        all.filter { it.status == IntegrationStatus.INTEGRATED }.forEach { entry ->
            assertEquals(
                "${entry.name} is reported as integrated but its licence is ${entry.compatibility}",
                LicenseCompatibility.PERMISSIVE,
                entry.compatibility
            )
            assertTrue("${entry.name} is not integrable yet is marked integrated", RepositoryInventory.isIntegrable(entry))
        }
    }

    @Test
    fun onlyLicenceCompatibleProjectsAreQueuedForIntegration() {
        RepositoryInventory.readyToIntegrate().forEach { entry ->
            assertEquals(
                "${entry.name} is queued for integration but its licence is ${entry.compatibility}",
                LicenseCompatibility.PERMISSIVE,
                entry.compatibility
            )
        }
    }

    @Test
    fun noRootOnlyProjectIsIntegratedOrQueued() {
        all.filter { it.privilege == PrivilegeRequirement.ROOT }.forEach { entry ->
            assertFalse(
                "${entry.name} needs a root shell and must not be integrated or queued",
                entry.status == IntegrationStatus.INTEGRATED ||
                    entry.status == IntegrationStatus.READY_TO_INTEGRATE
            )
        }
    }

    @Test
    fun anAdoptedCoverageClaimIsAlwaysIntegrated() {
        all.filter { it.coverage == Coverage.ADOPTED || it.coverage == Coverage.PARTIAL }
            .forEach { entry ->
                assertEquals(
                    "${entry.name} claims adopted coverage without being integrated",
                    IntegrationStatus.INTEGRATED,
                    entry.status
                )
            }
    }

    @Test
    fun studiedProjectsAreNeverClaimedAsIntegrated() {
        all.filter { it.coverage == Coverage.REFERENCE }.forEach { entry ->
            assertTrue(
                "${entry.name} was only studied, so it cannot be integrated or queued",
                entry.status == IntegrationStatus.REFERENCE_ONLY ||
                    entry.status == IntegrationStatus.INCOMPATIBLE
            )
        }
    }

    // ── The vocabulary is fully demonstrated by real projects ──

    @Test
    fun everyDocumentedStatusIsDemonstratedByARealProject() {
        assertEquals(
            IntegrationStatus.entries.toSet(),
            all.map { it.status }.toSet()
        )
    }

    @Test
    fun everyDocumentedPrivilegeIsDemonstratedByARealProject() {
        assertEquals(
            PrivilegeRequirement.entries.toSet(),
            all.map { it.privilege }.toSet()
        )
    }

    @Test
    fun everyLicenceVerdictIsDemonstratedByARealProject() {
        assertEquals(
            LicenseCompatibility.entries.toSet(),
            all.map { it.compatibility }.toSet()
        )
    }

    // ── Coverage of the app's own structure ──

    @Test
    fun theInventoryCoversAllSixCategoriesInTheAppOrder() {
        assertEquals(
            SonicCoreCategory.entries.map { it.title },
            InventoryCategory.entries.map { it.title }
        )
        InventoryCategory.entries.forEach { category ->
            assertTrue(
                "${category.title} has no audited project",
                RepositoryInventory.forCategory(category).isNotEmpty()
            )
        }
    }

    @Test
    fun countsAddUpToTheInventory() {
        assertEquals(
            all.size,
            RepositoryInventory.countByStatus().sumOf { it.second }
        )
        assertEquals(
            all.size,
            RepositoryInventory.countByCompatibility().sumOf { it.second }
        )
        assertEquals(all.size, RepositoryInventory.auditedCount)
        assertEquals(
            all.count { it.compatibility == LicenseCompatibility.PERMISSIVE },
            RepositoryInventory.compatible().size
        )
    }

    @Test
    fun everyReleaseNoteBelongsToAnAuditedProject() {
        val names = all.map { it.name }.toSet()
        RepositoryInventory.latestReleases.keys.forEach { name ->
            assertTrue("$name has a release note but is not in the inventory", names.contains(name))
        }
    }

    // ── Search and filters, as the Repository Manager uses them ──

    @Test
    fun aBlankQueryKeepsTheWholeInventory() {
        assertEquals(all, RepositoryInventory.search(""))
        assertEquals(all, RepositoryInventory.search("   "))
        assertEquals(all, RepositoryInventory.search())
    }

    @Test
    fun searchMatchesNameCapabilityLicenceAndOwnerCaseInsensitively() {
        assertTrue(RepositoryInventory.search("autoeq").any { it.name == "jaakkopasanen/AutoEq" })
        assertTrue(RepositoryInventory.search("AUTOEQ").any { it.name == "jaakkopasanen/AutoEq" })
        assertTrue(RepositoryInventory.search("rikkaapps").any { it.name == "RikkaApps/Shizuku-API" })
        assertTrue(RepositoryInventory.search("GPL-3.0").isNotEmpty())
        assertTrue(RepositoryInventory.search("no such repository exists").isEmpty())
    }

    @Test
    fun searchCombinesQueryCategoryAndStatus() {
        val audio = RepositoryInventory.search("", InventoryCategory.AUDIO)
        assertTrue(audio.isNotEmpty())
        assertTrue(audio.all { it.category == InventoryCategory.AUDIO })

        val gplAudio = RepositoryInventory.search("GPL", InventoryCategory.AUDIO)
        assertTrue(gplAudio.isNotEmpty())
        assertTrue(gplAudio.all { it.category == InventoryCategory.AUDIO })

        val integrated = RepositoryInventory.search("", null, IntegrationStatus.INTEGRATED)
        assertEquals(RepositoryInventory.integrated(), integrated)

        val impossible = RepositoryInventory.search("autoeq", InventoryCategory.SETTINGS)
        assertTrue(impossible.isEmpty())
    }

    @Test
    fun theFilteredResultIsAlwaysASubsetOfTheInventory() {
        InventoryCategory.entries.forEach { category ->
            IntegrationStatus.entries.forEach { status ->
                val result = RepositoryInventory.search("", category, status)
                assertTrue(result.all { all.contains(it) })
                assertTrue(result.all { it.category == category && it.status == status })
            }
        }
    }
}
