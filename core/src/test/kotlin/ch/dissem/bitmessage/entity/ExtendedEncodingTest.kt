/*
 * Copyright 2017 Christian Basler
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ch.dissem.bitmessage.entity

import ch.dissem.bitmessage.entity.valueobject.ExtendedEncoding
import ch.dissem.bitmessage.entity.valueobject.extended.Attachment
import ch.dissem.bitmessage.entity.valueobject.extended.Message
import ch.dissem.bitmessage.entity.valueobject.extended.Vote
import ch.dissem.bitmessage.factory.ExtendedEncodingFactory
import ch.dissem.bitmessage.utils.Bytes
import ch.dissem.bitmessage.utils.TestUtils
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test

/**
 * @author Christian Basler
 */
class ExtendedEncodingTest {
    @Test
    fun `ensure simple message is decoded`() {
        val extended =
            ExtendedEncodingFactory.unzip(Bytes.fromHex("78da9d59dd8e1bb715ee359fa097c4b65b498876babb76d0760bd970368eedb68e8daed3c0700c81d25012b333c3e99063ad1c0430d0a728d002bd289077caa59fa4df39244723693705b2f0da339c730ebff3c3f343ffe33f3f94da39b5d4ff9dd97cf3e32f9d10524afe4b3a2b57762dfd4acb453bbf967355c967b2755a1a2f1f0afe791eb85f2a7c374e2ab950ce8fe5dc96b59a7b3933956a36d2e9c6a8c2bc57ded84a2e6c532a10b9d678352b342d48674a53a846e6ca2be9adfcd3d58b2f33f96a05a190748d3d64ddd87726d74e5ebedcf81504417a6eaaa563018d56f4225595cb75633c3df7d191e04c3cab9c574521c4af656d6a69c2ab2cdd927639a959b0102f372f3742ecae6ef7afdb0668c2a229eb4297baf2ac5b2689932d4576f2809f89af01d3ae61d9af57ba921bdbd2f78f1ffee999269a2837ce3766d692943113555ae7648984f16fc6b5aa9057becd8d95d038ca95579fff5902497ccdf00064ad97fac6ebcab1bcd69139eec64dfcc9aa4d5b39e90abb2e3699105f60a3f8e13cfbdd583e37f3c63abbf009cfe5279fc84bb8db14ba61476ca929221a8d58c03e399471b660fd76c5decb3ebd456c54f3fcf4ec53125fb695f11bf93837016e231fdfd40ddcdb2d91cd679a4cca569bb5a6c8b72690a5cddb4267e22945b42532f1a2d2276e653d0798fc8d6c2b7a10e22b78859e66ac0e3da5b80a14613d3ce34b96a2671b20795bd68e390aab723ce14f615408543e1bdecc4c411aade12cf9ad034222afcdfc9a500a96472b248a313882bd80954f0a73ada59d7dabe73e8b30babde23b766a6c79482ec483070fc8f7b6f109362fc5e78cf51bbe391bcbf3b1bcf7762406dfdcfce1de3737a767f83dc7efbdc10e7db4c8703a121d93101154c2128911f2b043d3a80da912bccf6b85a19c81d80f07273083c4b788d10b71eb86b3436014e67a4ac2265fa8c2e99118464823215ee344c1d92d8242156bb5a1b482e8a1e496b8e4b5deac6d038266d9d2d1c8e4954628e88633563547e273aed514d485e20413222970db9ae370a6717460e8bf221fb1f8dccee968839abc6fb1d4445ae4862b8fbc55d2c72e9e1080fc0832cea81f3ffccbed51e9e6e3877f67f299df9ab82dbc81b5a2a3a3ff6da565e09543eccd6bb38da7141642951036b65dae90d2c14319a7d4f04b3e82067b61c2dc389271f93392f3ec8510b3762127e9753812a4a541d6928daa967a78767a3abaa07a02ba8c12b31eee065b2033a3d18865654eebebe1e9284511cc30e9bc9f2c330461d82912e5b46162e0eda0a2a97cf71d393d98f7b79da1a55dc879eb3cd40a5567536b216054327b81ea575be70c1528b8997822eb011372ee53dd68e6ab907314e556f63648b437a5ceba87ceac69e5c0ce0211b5688b696e504027f23b56e6c8e44717f26c1c5ee670a9d7b472b84165d7c3d1587c2f44ae173247facdf5347d1d223c8237a459c8d960da7d994e0764427c0f9fe9072f0070b80542aa4ed2de1c2937c5c2d1dbb13c3a7e7d5c1ee7af8e9f5e1c3fbf38beca8e17472396d668df362c3dc0d2d54fc0328e4b1ece1bad8f0f018cb610a3e0ef7635b990af9a568fe52060c33b0411ec05ef762bceef0f808a1039c911bb51db731210ea85c2019ceca93512d40030cd542d15cc3b39c863bd3dc6f1f04e57d65e4ff63c37daa605ce993d5264cda2985160a2d86af34e53d620817fe4fc13296b651ab7475f221593ad2955f56a6714032921b12d28339ebc53454b85116232f1980a2be70f8aff9f776a6257421851a779edee021597b87cf042886eb67b0c9f2ccbe41d21c46c19ff3de25a49f1401b928de5642207f9602ba01708c95dd0f715c887f7cfc781d7869c3e44d64a6c8d32e81c88ec71d3d86678f455758dd358b16617f2b83992c792f104a64e0d68cf8e19121a8e77b5ab4e4279ff7c17a44240f5541b428bd12e4146593b4265b9b729a9b62a8485a46d0fcf1630f9ef60dbb17c739651a9cdeea36920ba9896f7cf0db16f0f4cfc377074a9fcae5332ee2c35490fa31ea6c936d9d3f117e251fe8e4220dfd65658b2f28d45ffff8873b52abc6e2ad4f2771cae284e8d0a1d78578353418d1533cac2f4726dea314f1d53b6c2748547d05374f16aa9eab4166aaaa37986eb428935bfb64cc725a3f206f5238e62a14ed3b108b59b7671085542596052d0719c72a92d0d5e4b6709c430e9987a64b3ac2c771ec687bdd1b6f8b877c4945c4e9c555bce48634464980d1c95046e916a74adc97e4864ba19d378e8570a63869aaf1203d19359c9208c603771f067641d182eb5ebc9e73402c2a23557f2dcc0d498027802794cf28109389cee6053ea0a5d1491f1c4a7196a94c7218139954cccadb3daa6bd455bcd8913ade55d4d0d67973492e961ea2cc672a9fd144d229e6315aa487527bbe06bb23def0f4309dc6b8b025baf92fd0acd660127036890cf1ca4d1127eafc8921d2d9efb1b8687b80ffd4401931eda2178466267b72af4a7c13d34a224dba53884d50273c7c6cd636828fb2d5ffadc2122570eb7c4a1efdbc7874eafcafb5440cb6028a98a2f2dd645ec9c29cae3a01cbb34d4e882bcbc71e36efecaad7661b026d781af356e256f17415307e6a238839e85e3411fe265020d9714d07771a3da55aa84b11a45dd7e824061fdff37e73d501a32f919861ec3b5b3c220da87749e754f52e5396731c38520c80cbc69df467768c86d20e13d03e3cc841ac41303b69ae9b9a2ab876badebdbc6513e3bb1c784cbd9d28599217f91f8b572a153e0f8a4ca5dda86ce9eaaa28eb0c765db00922f9005d62b4ca17b372cd2b5351d38a8add71dbeae2e48a73dcf575b8fc66b271e89b96a3b06032c61a0496d0c1245b2d390ac00d1e99e81669ad7e1f645ce57740af98286340c9d088d72c032252c132a2169a62321a11ad0fedce5817e8221acf58b93dfe3df1e65aa1c77f7327794c637b381ab554973ec402f9783b7e34344a35b87e1ae3c266403c68566e0cd9e482152ab25c42b1e5ebbde6b1c26e2bd8e27d6bfd4ccfd0cb56e6ba166839bcdfbf79bc1e8a7f419897e1f32213e8acc49e2dd7a33064708de1842eca9d82584413e4ee694583876f742f2343bcf6e4854837e96b2c1c1f77b72a5d028385b62bb2a1d1c04778826b4c5cc971084c44a0179d705031d468e332e43d82093bd7b8aee82a223d7377561e606c78a15a062b65690b77386b3eda4d05506b606e38ecd782aa34842a16f75f245eb5f2c3ee7b2b93d4b57ded6cf525794edbe12fa2e1184d689eecb1aebeddc16d25654bed9da6a46f791bd8b14f1e41239a27705f4e412394b514258d325e9b2b16bce9b4561e79c86d27d17df08975a55aed7d5513310521a7e0bba157c72194c99a26339cf9096e9b619c6e02db6dc05ecdb355f99d8bbd011e22ff462822bb609eaefada61b214ee73054d0252465beb7228ec22c57dc6f516e2499bba076afab62a2dce26298bbb74ff1d8a98aaef27ae6e311b57fabcc0a203952a740beefdf849351d7e9266e119a3452012d4bcd85650f57a4df02732d3ab2642ff9a80a775aeb70afb74716d38749d3dec1349a891f5ccb8b3ffee2ac1b2b5326eb5d6c73d7e0a43c917c5548ff35f1f0e1ff00b5629026"))
        assertTrue(extended is ExtendedEncoding)
        assertTrue(extended?.content is Message)
        (extended?.content as Message).apply {
            assertEquals("Extended encoding on Windows works  -  but how ??", subject)
            assertNotNull(body)
            assertEquals(6233, body.length)
        }
    }


    @Test
    fun `ensure simple message is encoded`() {
        val input = Message.Builder()
            .subject("Test sübject")
            .body("test bödy")
            .build()

        assertEquals(input, ExtendedEncodingFactory.unzip(input.zip()))
    }

    @Test
    fun `ensure complete message is encoded and decoded`() {
        val input = Message.Builder()
            .addParent(TestUtils.randomInventoryVector())
            .addParent(TestUtils.randomInventoryVector())
            .subject("Test sübject")
            .body("test bödy")
            .addFile(
                Attachment.Builder()
                    .name("test.txt")
                    .type("text/plain")
                    .data("test".toByteArray(charset("UTF-8")))
                    .attachment()
                    .build()
            )
            .build()

        assertEquals(input, ExtendedEncodingFactory.unzip(input.zip()))
    }

    @Test
    fun `ensure vote is encoded and decoded`() {
        val input = Vote.Builder()
            .msgId(TestUtils.randomInventoryVector())
            .vote("+1")
            .build()

        assertEquals(input, ExtendedEncodingFactory.unzip(input.zip()))
    }
}
