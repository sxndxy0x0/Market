# Price Sync — Fabric Mod (skeleton)

> **Targets Minecraft 26.2** ("Chaos Cubed", June 2026). Since 26.1, Minecraft
> ships **unobfuscated** — Yarn mappings are discontinued, so this project uses
> Mojang's official mappings directly (`loom.officialMojangMappings()`), and
> Loom no longer remaps mods (`modImplementation`/`remapJar` don't exist
> anymore — just `implementation`/`jar`). Requires **Java 25** for the Gradle
> JVM (Loom 1.17, Fabric Loader 0.19.3, Fabric API 0.156.0+26.2 — versions
> confirmed against FabricMC's own official template, tag `26.2`).
> If you followed an older tutorial that mentions Yarn or `modImplementation`,
> that guide predates 26.1 and won't match this setup.

โครงไฟล์ตาม architecture ใน spec เดิม รันไม่ได้ทันที ต้องเติมส่วนที่ทำเครื่องหมาย `TODO` ก่อน

## โครงสร้าง

```
fabric-mod/
├── build.gradle, gradle.properties, settings.gradle   # Loom build setup
└── src/main/java/com/example/pricesync/
    ├── PriceSyncMod.java     # entrypoint, wires ทุกโมดูลเข้าด้วยกัน
    ├── event/EventManager.java     # ผูก Fabric events -> pipeline
    ├── gui/GuiReader.java          # อ่าน raw ItemStack จาก GUI ที่เปิดอยู่
    ├── parser/GuiParser.java       # ItemStack -> ชื่อ + lore (text)
    ├── parser/PriceParser.java     # lore text -> PriceEntry (regex)
    ├── cache/CacheManager.java     # เก็บ/เทียบราคาเดิม, persist เป็น cache.json
    ├── config/ConfigManager.java   # โหลด/เซฟ config.json
    ├── scheduler/Scheduler.java    # โหมด automatic: ยิง sync ตามรอบเวลา
    ├── api/ApiClient.java          # POST ไป backend + retry/queue
    └── util/                       # Logger, JsonBuilder, PriceEntry
```

## ยืนยันแล้วจากเซิร์ฟเวอร์จริง (SiamCraft.net, ส.ค. 2026)

- **Title**: `"WORTH (1/43)"` — มีเลขหน้าต่อท้าย ตรวจจับด้วย **prefix match** (`guiTitle = "WORTH"`) ไม่ใช่ exact match
- **ขนาด GUI**: 6 แถว = 54 ช่อง (`containerSlotCount = 54`, ค่า default ตรงอยู่แล้ว)
- **Lore**: มีราคาเดียว ไม่แยก buy/sell:
  ```
  ราคาต่อชิ้น: 🪙 1,069.02
  ราคาต่อสแตค: 🪙 68,417.28
  ```
  → แมปเข้า `PriceEntry.sell` (ราคาต่อชิ้น) และ `PriceEntry.stackPrice` (ราคาต่อสแตค, ฟิลด์เสริม)
  `buy` จะเป็น `-1` เสมอเพราะเซิร์ฟเวอร์นี้ไม่มีราคาซื้อแยก
  **ราคาเป็นทศนิยม** จึงเปลี่ยน `PriceEntry.buy/sell` จาก `long` เป็น `double` แล้ว
- คลิกไอเทมแค่ดูราคา ไม่ใช่ซื้อขายจริง — ไม่ต้องจัดการ click/confirm flow

⚠️ **ถ้า backend/website ทำแล้ว**: ต้องแก้ schema คอลัมน์ `buy_price`/`sell_price` จาก `INTEGER` เป็น `REAL` ให้รองรับทศนิยมด้วย (ยังไม่ได้แก้ใน backend skeleton ที่ทำไปก่อนหน้า)



1. ✅ **จุดตรวจจับ GUI เปิด** — ทำแล้ว ผ่าน `mixin/HandledScreenMixin.java` → `event/ScreenOpenCallback.java`
   → `EventManager` เทียบ `screen.getTitle()` กับ `config.guiTitle` (default `"Worth"`, แก้ใน config.json ได้)
   **ต้องแก้ `guiTitle` ให้ตรงกับชื่อ GUI จริงของเซิร์ฟเวอร์** ไม่งั้นจะไม่ trigger เลย
2. ✅ **`PriceParser` regex** — ปรับตาม lore จริงแล้ว (ดูหัวข้อด้านบน)
   ยังต้อง**เก็บตัวอย่าง lore ของไอเทมชนิดอื่นเพิ่ม** เผื่อบางไอเทมมี format ต่างจากนี้ (เช่น ไอเทมที่ราคาต่อสแตคไม่แสดง)
3. ✅ **ปุ่ม Refresh (`updateMode: refresh_button`)** — ทำแล้ว ผ่าน `event/KeybindManager.java`
   ผูกกับปุ่ม (default unbound, ผู้เล่นต้องไปตั้งเองใน Controls menu → หมวด "Price Sync")
   เรียก `EventManager.runNow()` เหมือนกันไม่ว่า `updateMode` จะเป็นอะไร
4. ✅ **exclude player inventory slots** — ทำแล้ว ผ่าน `config.containerSlotCount` (default `54`
   = ขนาด generic_9x6 chest GUI) `GuiReader` จะอ่านแค่ N slot แรกเท่านั้น
   **ต้องปรับเลขนี้ให้ตรงกับขนาด GUI จริงของเซิร์ฟเวอร์** (เช่น 27 ถ้าเป็น generic_9x3)
5. ✅ **ERROR HANDLING (log + never crash)** — ทำแล้วตาม spec เดิม:
   - `Logger` เขียน log ลงไฟล์ `config/price-sync/latest.log` ด้วยแล้ว (เดิม console อย่างเดียว)
   - `EventManager.safeRunSyncPipeline()` ครอบทุกจุดที่เรียก pipeline (mixin callback, tick poll, scheduler, keybind) ด้วย try-catch กัน exception หลุดไปทำเกมค้าง/แครช — error จะแค่ log ไว้แล้วข้ามรอบนั้นไป
   - `ApiClient` มี retry (3 ครั้ง) + queue ค้างไว้ส่งใหม่รอบหน้าอยู่แล้วตั้งแต่แรก
6. ✅ **Config validation** — ทำแล้วใน `ConfigManager.validate()` เรียกอัตโนมัติหลัง `load()`:
   - `config.json` เขียนผิด/พังทั้งไฟล์ (invalid JSON) → catch แล้ว fallback เป็น default ทั้งหมด ไม่ crash
   - `updateMode` ค่าที่ไม่รู้จัก (ไม่ใช่ `manual`/`automatic`/`refresh_button`) → fallback `manual` + warn
   - `updateInterval` ≤ 0 → fallback `86400`
   - `containerSlotCount` ติดลบ → fallback `54`
   - `guiTitle` ว่างเปล่า → fallback `"WORTH"` (สำคัญมาก เพราะถ้าว่างจะไม่ sync อะไรเลย)
   - `apiUrl` ว่าง/ไม่ใช่ http(s) URL ที่ถูกต้อง → แค่ warn ไม่ reset (ให้ `ApiClient` จัดการตอนส่งจริงเอง)
   ทุกจุดที่แก้ log เป็น `warn` ใน `latest.log` ให้เห็นว่าค่าไหนถูกแก้ให้อัตโนมัติ
7. ✅ **`/pricesync status` และ `/pricesync sync`** — เพิ่มคำสั่งในเกม (ผ่าน `event/CommandManager.java`)
   - `/pricesync` หรือ `/pricesync status` — โชว์ mode, guiTitle, จำนวนไอเทมที่แคชไว้, จำนวน payload ที่ค้างส่ง (retry queue), เวลา sync ล่าสุด
   - `/pricesync sync` — สั่ง sync มือทันที (เหมือนกดปุ่ม refresh keybind แต่ไม่ต้องไปตั้งปุ่มก่อน)
8. ✅ **`ApiClient` แข็งแรงขึ้น** — เดิม retry ทันทีไม่มีหน่วง (spam backend ถ้าเน็ตมีปัญหา) และคิวค้างส่งไม่มีเพดาน:
   - เปลี่ยนเป็น **exponential backoff** (1s → 2s → 4s) ก่อน retry แต่ละครั้ง
   - จำกัดคิวค้างส่งไว้ที่ **200 รายการ** ถ้าเกินจะทิ้งอันเก่าสุดทิ้ง (เก็บข้อมูลใหม่กว่าไว้)

### หมายเหตุเรื่องการตรวจจับ GUI (เปลี่ยนจาก mixin เป็น Fabric ScreenEvents)

**อัปเดตสำคัญหลัง CI จริง**: เดิมใช้ mixin เขียนเอง (`HandledScreenMixin`) hook เข้า `HandledScreen.init()` แต่พอ build จริงกับ 26.2 พบว่าการอ่าน "GUI ที่เปิดอยู่ตอนนี้" จาก `Minecraft` โดยตรงเดายากมาก (ลองมาแล้ว 3 ชื่อ: `screen`, `currentScreen`, `gui.getScreen()` ไม่มีอันไหนถูกเลย) **เปลี่ยนมาใช้ `net.fabricmc.fabric.api.client.screen.v1.ScreenEvents` ของ Fabric เองแทนทั้งหมด** — ลบ `HandledScreenMixin`, `ScreenOpenCallback`, `price_sync.mixins.json` ออกหมด ไม่มี mixin เขียนเองในโปรเจกต์นี้อีกต่อไป

- `GuiReader` เก็บสถานะ "screen ที่เปิดอยู่ตอนนี้" เองผ่าน `ScreenEvents.AFTER_INIT` (ตอนเปิด) และ `ScreenEvents.remove(screen)` (ตอนปิด) — API นี้เสถียรมาตั้งแต่ Fabric API 0.40 (1.17) ไม่เคยเปลี่ยน signature แม้ Minecraft จะเปลี่ยน internal field ไปเรื่อยๆ
- `EventManager` ก็เปลี่ยนมา register `ScreenEvents.AFTER_INIT` ตรงๆ แทน `ScreenOpenCallback.EVENT` เดิม กรองด้วย `instanceof AbstractContainerScreen` แล้วเช็ค `guiTitle` เหมือนเดิม (ทำงานกับ**ทุก** container GUI ที่เปิด ไม่ใช่แค่ /worth ดังนั้นการกรองด้วย `guiTitle` ยังสำคัญเหมือนเดิม)
- ถ้าอยากดีบัก title จริงที่เจอ ให้เปิด `"debug": true` ใน config.json แล้วดู log บรรทัด `Ignoring screen with title "..."`
- `fabric.mod.json` ไม่มี `"mixins"` key แล้ว (ลบออกเพราะไม่มี mixin ให้ประกาศ)

## หลายหน้า / หลายหมวดหมู่ ต้องทำยังไง

**คุณยังต้องกดเปลี่ยนหน้า/หมวดหมู่เองครับ** mod ไม่ได้กดให้อัตโนมัติ — สิ่งที่ mod ทำให้คือ**จับข้อมูลอัตโนมัติทุกครั้งที่คุณกด** ไม่ต้องกดปุ่ม sync เอง

รายละเอียดทางเทคนิค: ตอนกดเปลี่ยนหน้า (เช่น 1/43 → 2/43) เซิร์ฟเวอร์แค่อัปเดตของในช่อง ไม่ได้เปิดหน้าต่างใหม่ (ไม่มี event เปิดจอใหม่ยิงซ้ำ) ดังนั้นแค่ hook ตอนเปิด GUI (`ScreenEvents.AFTER_INIT`) อย่างเดียวจะจับได้แค่หน้าแรกที่เปิดมา — จึงเพิ่ม **tick-based poll** ใน `EventManager` (เช็คทุก ~1 วินาทีขณะ GUI ตรงชื่อยังเปิดอยู่) เพื่อจับหน้า/หมวดที่กำลังแสดงอยู่ ณ ขณะนั้นแทน แคชกันไม่ให้ยิง API ซ้ำถ้าราคาไม่เปลี่ยน จึงปลอดภัยแม้เช็คบ่อย

**ถ้าอยากเก็บให้ครบทั้ง 43 หน้า** ตอนนี้ต้องไล่กดดูเองทีละหน้า (browse ทั้งหมด mod จะ sync ให้อัตโนมัติทุกหน้าที่คุณผ่าน) ยังไม่มีระบบกดหน้าถัดไปให้อัตโนมัติ — ถ้าต้องการ auto-page ในอนาคตต้องส่ง slot-click packet เอง ซึ่งซับซ้อนขึ้นและบางเซิร์ฟเวอร์อาจถือเป็นการ automate ที่ผิดกฎ ต้องเช็ก TOS ของเซิร์ฟเวอร์ก่อน

## เทียบกับ official template ตรงๆ (FabricMC/fabric-example-mod tag `26.2`)

หลัง diff กับ template ทางการเจอจุดที่ต้องแก้เพิ่ม:

- **ลบ `mappings loom.officialMojangMappings()`** ออกจาก `build.gradle` — official ไม่ประกาศบรรทัดนี้เลย เพราะตั้งแต่ 26.1 MC unobfuscated แล้ว Loom จัดการ mapping เองอัตโนมัติโดยไม่ต้องขอ mappings dependency ใดๆ (ถ้าเคยเห็น AI/tutorial อื่นใส่บรรทัดนี้ไว้ ตัดออกได้เลย)
- **plugin id** เปลี่ยนจาก short alias `'fabric-loom'` เป็น full id `'net.fabricmc.fabric-loom'` (ปลอดภัยกว่า ตรงกับ official เป๊ะ)
- **`fabric.mod.json`**: เพิ่ม `"java": ">=25"` (ให้ Fabric Loader เตือนชัดเจนถ้ารันด้วย Java เก่ากว่านี้ แทนที่จะ error มั่วๆ), เปลี่ยน `"minecraft": "26.2"` → `"~26.2"` (รองรับ patch version ในอนาคตเช่น 26.2.1), อัปเดต `fabricloader` เป็น `>=0.19.3`
- **`settings.gradle`**: เพิ่ม `name = 'Fabric'` ให้ maven repo block และเพิ่ม `mavenCentral()` ให้ตรงกับ official
- เพิ่ม `tasks.withType(JavaCompile).configureEach { it.options.release = 25 }` กัน bytecode target เพี้ยนถ้า toolchain เครื่องคุณมี JDK อื่นปนอยู่

**สิ่งที่ตั้งใจไม่ทำตาม official**: official ใช้ `loom { splitEnvironmentSourceSets() }` แยก `src/main` (ทั้งสองฝั่ง) กับ `src/client` (client-only) — mod นี้เป็น client-only ล้วน (`"environment": "client"`) จึงไม่จำเป็นต้องแยก เก็บทุกอย่างไว้ใน `src/main/java` เหมือนเดิม ง่ายกว่าและได้ผลเท่ากัน

## CI (GitHub Actions)

`.github/workflows/build.yml` — รัน `./gradlew test` + `./gradlew build` อัตโนมัติทุกครั้งที่ push/PR เข้า `main` (ใช้ JDK 25 ผ่าน Temurin) ถ้า push ขึ้น GitHub แล้วจะได้ผล build/test ให้เช็คโดยไม่ต้องรอเปิดคอมตัวเองทุกครั้ง — jar ที่ build ได้จะอัปโหลดเป็น artifact ให้ดาวน์โหลดจากหน้า Actions ด้วย

## Gradle Wrapper

รวม `gradlew` / `gradlew.bat` / `gradle/wrapper/gradle-wrapper.jar` มาให้แล้ว (ดึงมาจาก [FabricMC/fabric-example-mod](https://github.com/FabricMC/fabric-example-mod) tag `26.1.2` ซึ่งเป็น template ทางการของ Fabric ที่อัปเดตรองรับ Minecraft version ใหม่แล้ว) ใช้ **Gradle 9.5.1** — ไม่ต้องติดตั้ง Gradle เองในเครื่อง แค่มี Java 25 ก็รัน `./gradlew build` ได้เลย

## Bug จริงที่เจอจาก CI (ส.ค. 2026) — Fabric API เปลี่ยน API อีกรอบใน 26.2

รัน CI จริงแล้วเจอ compile error 9 จุด ทั้งหมดเป็นเพราะ Fabric API/Mojang mappings เปลี่ยนชื่อ/ย้าย package **เฉพาะใน 26.2** (แม้จะเพิ่งพอร์ตมาจาก 26.1 ก็ยังไม่พอ) แก้ครบแล้ว:

| เดิม (26.1 หรือก่อนหน้า) | ใหม่ (26.2) |
|---|---|
| `net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper` | `net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper` |
| `new KeyMapping(name, keycode, "category.string")` | `new KeyMapping(name, InputConstants.Type.KEYSYM, keycode, KeyMapping.Category)` — ต้อง register category เป็น object ก่อนผ่าน `KeyMapping.Category.register(Identifier)` |
| `net.fabricmc.fabric.api.client.command.v2.ClientCommandManager` | เปลี่ยนชื่อคลาสเป็น `ClientCommands` (package เดิม) |
| `Minecraft.getInstance().screen` | ~~`Minecraft.getInstance().gui.getScreen()`~~ ผิด! ที่ถูกคือ **`Minecraft.getInstance().currentScreen`** (field เดิม ยังอยู่ที่ `Minecraft` เหมือนเดิม ไม่ได้ย้าย) — Fabric blog พูดถึงแค่ metehod **`setScreen()`** ที่ย้ายไป `gui.setScreen()`, การอ่านค่ายังผ่าน field `currentScreen` แบบเดิม (เจอจาก [Custom Screens doc](https://docs.fabricmc.net/develop/rendering/gui/custom-screens) ที่อัปเดตหลัง 26.2 ออกจริง) |
| `net.minecraft.resources.ResourceLocation` | เปลี่ยนชื่อเป็น `net.minecraft.resources.Identifier` (ตั้งแต่ 1.21.11/26.1 — Mojang "ยืม" ชื่อจาก Yarn มาใช้) |
| lang key `"category.price_sync"` | ต้องเป็น `"key.category.<namespace>.<path>"` เช่น `"key.category.price_sync.general"` |

**บทเรียนสำคัญ**: การเปลี่ยน API แบบนี้เกิดขึ้น**ทุกเวอร์ชันหลัก** ของ Minecraft ตอนนี้ (26.1 → 26.2 ก็เปลี่ยนอีกรอบ) ถ้าอัปเดต `minecraft_version` ในอนาคต (เช่นเป็น 26.3+) ให้เตรียมใจว่าอาจมี compile error แบบนี้อีก — วิธีเช็คเร็วสุดคือ build แล้วดู error, ไม่ใช่เดาจากความรู้เดิม

## Bug จริงตอนรันในเกม (ส.ค. 2026) — `NoClassDefFoundError: okhttp3/Callback`

Build/test ผ่าน CI หมดแล้ว แต่พอเอา jar ไปรันในเกมจริง crash ทันทีตอน init เพราะ **`implementation` ใน Gradle แค่ทำให้ compile ผ่าน ไม่ได้ฝัง library เข้าไปใน jar สุดท้าย** — เกมจริงหาคลาส OkHttp ไม่เจอเลย crash

**แก้โดยเปลี่ยนไปใช้ `java.net.http.HttpClient`** (มากับ JDK ตั้งแต่ Java 11) แทน OkHttp ไปเลย — เหตุผลที่ไม่ใช้ Loom's `include` กับ OkHttp เพราะ OkHttp พึ่ง library ต่อ (Okio + Kotlin stdlib) แบบ transitive ซึ่ง `include` ไม่ดึงให้อัตโนมัติ ต้องพึ่ง Shadow plugin เพิ่ม (ซับซ้อนเกินความจำเป็นสำหรับแค่ POST request เดียว) ส่วน **Gson ยังใช้ต่อได้** เพราะไม่มี transitive dependency เลย ใช้ `implementation include('com.google.code.gson:gson:2.11.0')` ฝังเข้า jar ได้ตรงๆ

**บทเรียน**: dependency ไหนก็ตามที่ประกาศด้วย `implementation` เฉยๆ ใน mod project จะ**ใช้ได้แค่ตอน build/test เท่านั้น** ถ้าไม่ใช้ `include` (หรือมี transitive deps ก็ต้องใช้ shadow plugin) จะ crash ตอนรันจริงเสมอ — Gradle/CI ไม่มีทางเตือนเรื่องนี้ได้เพราะ compile ผ่านปกติ ต้องรันในเกมจริงถึงจะเจอ

## Bug จริงจาก log ในเกม (ส.ค. 2026) — Title เป็น Unicode small-caps ไม่ใช่ตัวอักษรธรรมดา

หลัง mod รันได้แล้ว เช็ค `/pricesync status` พบ `cached items=0` ตลอด — เปิด `debug: true` แล้วดู log เจอสาเหตุจริง:

```
[DEBUG] Ignoring screen with title "ᴡᴏʀᴛʜ (1/43)" (expected prefix "WORTH")
```

เซิร์ฟเวอร์นี้ใช้ฟอนต์ **Unicode small-caps** ตกแต่ง title (`ᴡᴏʀᴛʜ` ใช้ codepoint คนละตัวกับ `WORTH` ธรรมดาเลย ไม่ใช่แค่ตัวพิมพ์ใหญ่-เล็ก) `.toLowerCase()` ธรรมดาไม่ช่วยเพราะมันเป็นตัวอักษรคนละชุด (Unicode Phonetic Extensions block) ทำให้ prefix match ไม่มีวันตรงเลย

**แก้แล้ว**: เพิ่ม `normalizeTitle()` ใน `EventManager` แปลง small-caps กลับเป็น ASCII ปกติก่อนเทียบ (`SMALL_CAPS_TO_ASCII` map) ตอนนี้ `guiTitle: "WORTH"` ใน config ใช้ตัวอักษรธรรมดาได้เลย ไม่ต้องก๊อปปี้ Unicode พิเศษมาใส่

## Test

```bash
./gradlew test
```

มี unit test 3 ไฟล์ใน `src/test/`:
- **`PriceParserTest`** — ใช้ lore จริงจากรูปที่แคปมา (spawner: `1,069.02` / `68,417.28`) พร้อม edge case (ปุ่มหมวดหมู่ไม่มีราคา, ไม่มีราคาต่อสแตค, ตัวเลขหลักล้าน)
- **`CacheManagerTest`** — เช็ค diff/update logic หลักของ spec ("ส่งเฉพาะตอนราคาเปลี่ยน") รวมเคส regression ของบั๊ก `stackPrice` ที่เจอก่อนหน้านี้ และเช็คว่า cache รอดจากการ restart (เขียน/อ่านไฟล์จริงใน temp dir)
- **`JsonBuilderTest`** — เช็คว่า JSON ที่ mod ส่งออกตรงกับที่ backend คาดหวังเป๊ะ (`server`, `timestamp`, `prices[].{id,name,buy,sell,stackPrice}`)

ทั้งหมดรันได้โดยไม่ต้องมีเกม Minecraft เพราะไม่มีคลาสไหนพึ่ง Minecraft class เลย

## Build

```bash
./gradlew build
```

(ต้องมี Java 25 และเน็ตให้ Gradle โหลด Fabric Loom + Mojang mappings)
