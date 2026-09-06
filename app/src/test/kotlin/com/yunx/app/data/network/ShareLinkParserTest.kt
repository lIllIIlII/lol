/*
 * YunX (云析) - A network drive share-link parser and high-speed downloader for Android.
 * Copyright (C) 2026 CYQawa
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.yunx.app.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareLinkParserTest {

    @Test
    fun parsesAllSupportedPlatforms() {
        val cases = listOf(
            "https://pan.quark.cn/s/Abc123?pwd=a1B2" to (SharePlatform.QUARK to "Abc123"),
            "https://drive.uc.cn/s/Abc123" to (SharePlatform.UC to "Abc123"),
            "https://pan.xunlei.com/s/Abc_123-xy" to (SharePlatform.XUNLEI to "Abc_123-xy"),
            "https://pan.baidu.com/s/1Abc_123-xy?pwd=9xYz" to (SharePlatform.BAIDU to "Abc_123-xy"),
            "https://yun.139.com/shareweb/#/w/i/Abc_123" to (SharePlatform.C139 to "Abc_123"),
            "https://www.123pan.com/s/2785Vv-T4Ded" to (SharePlatform.PAN123 to "2785Vv-T4Ded")
        )

        cases.forEach { (text, expected) ->
            val parsed = ShareLinkParser.parse(text)!!
            assertEquals(expected.first, parsed.platform)
            assertEquals(expected.second, parsed.shareId)
        }
    }

    @Test
    fun explicitTextPasswordIsExtracted() {
        val parsed = ShareLinkParser.parse("链接 https://drive.uc.cn/s/Abc123 提取码：a1B2")!!
        assertEquals("a1B2", parsed.pwd)
    }

    @Test
    fun parsesCtfileLinks() {
        // 3 段（/f/）与 2 段（/file/）形态 + 镜像域名 + ?p= 访问密码 + 文案密码
        val f = ShareLinkParser.parse("https://url78.ctfile.com/f/235978-1320342970-0dad31")!!
        assertEquals(SharePlatform.CTFILE, f.platform)
        assertEquals("f:235978-1320342970-0dad31", f.shareId)
        assertNull(f.pwd)

        val withPwd = ShareLinkParser.parse("https://url78.ctfile.com/f/235978-1320342970-0dad31?p=336307")!!
        assertEquals("336307", withPwd.pwd)

        val twoSeg = ShareLinkParser.parse("https://url05.ctfile.com/file/23944505-588439140")!!
        assertEquals("file:23944505-588439140", twoSeg.shareId)

        val mirror = ShareLinkParser.parse("https://545c.com/f/235978-1320342979-f5e62d (访问密码: 336307)")!!
        assertEquals(SharePlatform.CTFILE, mirror.platform)
        assertEquals("336307", mirror.pwd)

        val dir = ShareLinkParser.parse("https://url78.ctfile.com/dir/abc123456")!!
        assertEquals("dir:abc123456", dir.shareId)
    }

    @Test
    fun parsesWenshushuLinks() {
        val tid = ShareLinkParser.parse("https://www.wenshushu.cn/f/3yvoreh54ry")!!
        assertEquals(SharePlatform.WENSHUSHU, tid.platform)
        assertEquals("3yvoreh54ry", tid.shareId)

        val sub = ShareLinkParser.parse("https://f.wenshushu.cn/f/ikmib9m0b39")!!
        assertEquals(SharePlatform.WENSHUSHU, sub.platform)
        assertEquals("ikmib9m0b39", sub.shareId)
    }

    @Test
    fun rejectsUnrelatedUrl() {
        assertNull(ShareLinkParser.parse("https://example.com/s/Abc123"))
    }
}
