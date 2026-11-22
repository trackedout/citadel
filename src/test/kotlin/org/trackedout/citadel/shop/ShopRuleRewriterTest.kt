package org.trackedout.citadel.shop

import org.trackedout.client.models.SettingsLeagues
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShopRuleRewriterTest {
    private val rewriter = ShopRuleRewriter(
        SettingsLeagues(
            practice = "do2-s2-practice",
            hardcore = "do2-s2-hardcore",
            advancements = "do2-s2-comp",
            shopRewriteCompetitive = "do2-s3-comp",
            phaseFilter = "do2-s2-comp",
        )
    )

    @Test
    fun `previous practice slug is rewritten`() {
        assertEquals("do2-s2-practice:CROWNx10=SHARDx1", rewriter.rewrite("practice:CROWNx10=SHARDx1"))
    }

    @Test
    fun `previous hardcore slug is rewritten`() {
        assertEquals("do2-s2-hardcore:CROWNx10=SHARDx1", rewriter.rewrite("hardcore:CROWNx10=SHARDx1"))
    }

    @Test
    fun `previous season slug is rewritten`() {
        assertEquals("do2-s2-comp:TOMEx1=SHARDx5,CROWNx2", rewriter.rewrite("season-2:TOMEx1=SHARDx5,CROWNx2"))
    }

    @Test
    fun `repeat marker is kept`() {
        assertEquals("do2-s2-practice:CROWNx10=SHARDx1:r", rewriter.rewrite("practice:CROWNx10=SHARDx1:r"))
    }

    @Test
    fun `legacy char rule is rewritten to the slug`() {
        assertEquals("do2-s2-practice:CROWNx10=SHARDx1", rewriter.rewrite("pCROWNx10=pSHARDx1"))
    }

    @Test
    fun `current slug is left alone`() {
        assertNull(rewriter.rewrite("do2-s2-practice:CROWNx10=SHARDx1"))
    }

    @Test
    fun `legacy competitive char names the settings' league`() {
        assertEquals("do2-s3-comp:TOMEx1=SHARDx5", rewriter.rewrite("cTOMEx1=cSHARDx5"))
    }
}
