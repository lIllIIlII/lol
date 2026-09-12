package com.yunx.app.data.network

enum class SharePlatform { QUARK, UC, XUNLEI, BAIDU, C139, PAN123, LANZOU, ILANZOU, COWTRANSFER, FEIJI, CTFILE, WENSHUSHU, CLOUD189 }

data class ParsedShare(
    val shareId: String,
    val pwd: String?,
    val platform: SharePlatform
)

object ShareLinkParser {

    private val urlRegex = Regex("""https?://[^\s]+""")
    private val quarkShareIdRegex = Regex("""pan\.quark\.cn/s/([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
    private val ucShareIdRegex = Regex("""drive\.uc\.cn/s/([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
    private val xunleiShareIdRegex = Regex("""pan\.xunlei\.com/s/([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
    private val baiduShareIdRegex = Regex("""pan\.baidu\.com/s/(1[A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
    private val c139ShareIdRegex = Regex("""yun\.139\.com/shareweb/.*?/w/i/([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
    private val pan123ShareIdRegex = Regex("""123(?:865|pan)\.(?:com|cn)/s/([A-Za-z0-9]+-[A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
    private val pan123ShareSubRegex = Regex("""share\.123pan\.cn/123pan/([A-Za-z0-9-]+)""", RegexOption.IGNORE_CASE)
    private val pan123SrrRegex = Regex("""api/srr\?sk=([A-Za-z0-9-]+)""", RegexOption.IGNORE_CASE)
    private val ilanzouShareIdRegex = Regex(
        """ilanzou[a-z]{0,2}\.(?:com|cn|net)/(?:s/)?([A-Za-z][0-9A-Za-z_-]{5,})""",
        RegexOption.IGNORE_CASE
    )
    private val lanzouShareIdRegex = Regex(
        """(?:[0-9A-Za-z]+\.)?(?:lanzo[u]?[a-z]{0,2}|lan(?:pw|pv|zn|zr)|wwurl|wurl|wwork)\.(?:com|cn|net|cc|me)(?:/[a-zA-Z]+)?/(?:s/)?([A-Za-z][0-9A-Za-z_-]{4,})""",
        RegexOption.IGNORE_CASE
    )
    private val cloud189ShareIdRegex = Regex(
        """cloud\.189\.cn/(?:t/|share\.html\?id=)?([A-Za-z0-9_-]{6,})""",
        RegexOption.IGNORE_CASE
    )
    private val cowShareIdRegex = Regex("""cowtransfer\.com/s/([0-9A-Za-z-]{10,})""", RegexOption.IGNORE_CASE)
    private val feijiShareIdRegex = Regex("""feiji(?:pan|x)\.com/s/([0-9A-Za-z-]{4,})""", RegexOption.IGNORE_CASE)
    private val ctfileShareIdRegex = Regex(
        """(?:[a-z0-9]+\.)?(?:ctfile|pipipan|545c)\.com/(f|file|dir)/([a-zA-Z0-9-]{5,})""",
        RegexOption.IGNORE_CASE
    )
    private val wssShareIdRegex = Regex(
        """(?:[a-z]+\.)?wenshushu\.cn/f/([a-zA-Z0-9]{8,20})""",
        RegexOption.IGNORE_CASE
    )
    private val pwdInUrlRegex = Regex("""[?&]pwd=([A-Za-z0-9]+)""")
    private val codeInUrlRegex = Regex("""[?&]code=([A-Za-z0-9]+)""")
    private val pInUrlRegex = Regex("""[?&]p=([A-Za-z0-9]{2,12})""")
    private val pwdInTextRegex = Regex("""(?:提取码|访问码|访问密码|密码)[：:]\s*([A-Za-z0-9]{3,12})""")

    fun parse(text: String): ParsedShare? {
        val url = urlRegex.find(text.trim())?.value
            ?.trimEnd('。', '，', ',', '；', ';', ')', ']', '}', '"', '\'')
            ?: return null
        quarkShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            val pwd = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.QUARK)
        }
        ucShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            val pwd = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.UC)
        }
        xunleiShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            val pwd = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.XUNLEI)
        }
        baiduShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            val surl = sid.removePrefix("1")
            val pwd = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = surl, pwd = pwd, platform = SharePlatform.BAIDU)
        }
        c139ShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            val pwd = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.C139)
        }
        pan123ShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            val pwd = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.PAN123)
        }
        pan123ShareSubRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            val pwd = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.PAN123)
        }
        pan123SrrRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            val pwd = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.PAN123)
        }
        ilanzouShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            if (sid.length < 6) return@let
            val pwd = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: codeInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.ILANZOU)
        }
        lanzouShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            if (sid.length < 6) return@let
            val pwd = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.LANZOU)
        }
        cloud189ShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            if (sid.length < 6) return@let
            val pwd = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.CLOUD189)
        }
        cowShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            val pwd = pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.COWTRANSFER)
        }
        feijiShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            if (sid.length < 5) return@let
            val pwd = codeInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
            return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.FEIJI)
        }
        ctfileShareIdRegex.find(url)?.groupValues?.let { g ->
            val kind = g.getOrNull(1).orEmpty()
            val sid = g.getOrNull(2).orEmpty()
            if (sid.length >= 6) {
                val pwd = pInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                    ?: pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                    ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
                return ParsedShare(shareId = "$kind:$sid", pwd = pwd, platform = SharePlatform.CTFILE)
            }
        }
        wssShareIdRegex.find(url)?.groupValues?.getOrNull(1)?.let { sid ->
            if (sid.length in 8..20) {
                val pwd = pInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                    ?: pwdInUrlRegex.find(url)?.groupValues?.getOrNull(1)
                    ?: pwdInTextRegex.find(text)?.groupValues?.getOrNull(1)
                return ParsedShare(shareId = sid, pwd = pwd, platform = SharePlatform.WENSHUSHU)
            }
        }
        return null
    }
}
