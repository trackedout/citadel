package org.trackedout.citadel.shop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShopRuleRewriterTest {
    @Test
    fun `previous practice slug is rewritten`() {
        assertEquals("do2-s2-practice:CROWNx10=SHARDx1", ShopRuleRewriter.rewrite("practice:CROWNx10=SHARDx1"))
    }

    @Test
    fun `previous hardcore slug is rewritten`() {
        assertEquals("do2-s2-hardcore:CROWNx10=SHARDx1", ShopRuleRewriter.rewrite("hardcore:CROWNx10=SHARDx1"))
    }

    @Test
    fun `previous season slug is rewritten`() {
        assertEquals("do2-s2-comp:TOMEx1=SHARDx5,CROWNx2", ShopRuleRewriter.rewrite("season-2:TOMEx1=SHARDx5,CROWNx2"))
    }

    @Test
    fun `repeat marker is kept`() {
        assertEquals("do2-s2-practice:CROWNx10=SHARDx1:r", ShopRuleRewriter.rewrite("practice:CROWNx10=SHARDx1:r"))
    }

    @Test
    fun `legacy char rule is rewritten to the slug`() {
        assertEquals("do2-s2-practice:CROWNx10=SHARDx1", ShopRuleRewriter.rewrite("pCROWNx10=pSHARDx1"))
    }

    @Test
    fun `current slug is left alone`() {
        assertNull(ShopRuleRewriter.rewrite("do2-s2-practice:CROWNx10=SHARDx1"))
    }
}
