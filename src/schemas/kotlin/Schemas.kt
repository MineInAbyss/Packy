import com.mineinabyss.geary.papermc.schemas.GearyPrefabSchemas.gearyPrefabs
import com.mineinabyss.jsonschema.writer.writeSchemas
import com.mineinabyss.packy.config.PackyConfig
import com.mineinabyss.packy.config.PackyMenu
import com.mineinabyss.packy.config.PackyTemplates

fun main(args: Array<String>) = writeSchemas(args) {
    schema("packy/config", listOf("**/plugins/Packy/config.yml", "**/plugins/Packy/config.yml.peb"), PackyConfig.serializer())
    schema("packy/menu", listOf("**/plugins/Packy/menu.yml"), PackyMenu.serializer())
    schema("packy/templates", listOf("**/plugins/Packy/templates.yml"), PackyTemplates.serializer())
    gearyPrefabs("packy", listOf("com.mineinabyss.packy"))
}
