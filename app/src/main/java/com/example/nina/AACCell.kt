package com.example.nina

import androidx.annotation.Keep
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.google.gson.Gson
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.annotations.JsonAdapter
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.util.UUID

/**
 * Modèle de données pour un bouton AAC.
 * Utilise SnapshotStateList pour que Compose détecte les ajouts à l'intérieur des dossiers.
 *
 * - id : identifiant unique, pour distinguer deux pictos identiques et retrouver un picto même après modification.
 * - spokenText : texte prononcé quand on touche le picto (ex. "Toilettes" -> "J'ai besoin d'aller aux toilettes").
 *   Le libellé (label) reste ce qui est ajouté à la phrase.
 *
 * @Keep : enregistré en JSON (Gson) ; la réduction du code ne doit ni renommer ni retirer ses champs.
 */
@Keep
data class AACCell(
    val label: String,
    val colorLong: Long,
    val imageUrl: String? = null,
    val audioPath: String? = null,
    val isFolder: Boolean = false,
    val isQuickPhrase: Boolean = false,
    val isHidden: Boolean = false,
    val imageRes: Int? = null,
    @field:JsonAdapter(CellContentAdapter::class) // Voir CellContentAdapter : le type des éléments est explicite
    val content: SnapshotStateList<AACCell> = mutableStateListOf(),
    val id: String = newCellId(),
    val spokenText: String? = null,
    // Adresse web d'origine de l'image, gardée quand l'image est enregistrée dans la tablette :
    // sur une autre tablette (sauvegarde restaurée), elle permet de retélécharger l'image
    val sourceUrl: String? = null
) {
    /** Ce que la synthèse vocale dit quand on touche le picto. */
    val textToSpeak: String
        get() = spokenText?.takeIf { it.isNotBlank() } ?: label
}

fun newCellId(): String = UUID.randomUUID().toString()

/**
 * Lecture et écriture JSON du contenu d'un dossier. Gson saurait le faire seul en lisant la signature générique
 * du champ (SnapshotStateList<AACCell>), mais la réduction du code (R8) efface cette signature : les éléments
 * reviendraient en simples tables et le chargement des grilles planterait. Ici, le type des éléments est explicite.
 * Le format écrit est le même qu'avant (un tableau de pictos) : les sauvegardes restent compatibles.
 */
@Keep
class CellContentAdapter : TypeAdapterFactory {
    override fun <T> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        if (type.rawType != SnapshotStateList::class.java) return null
        // Résolu au premier usage : l'adaptateur d'AACCell est justement en cours de construction quand on arrive ici
        val element by lazy { gson.getAdapter(AACCell::class.java) }
        val adapter = object : TypeAdapter<SnapshotStateList<AACCell>>() {
            override fun write(out: JsonWriter, value: SnapshotStateList<AACCell>?) {
                if (value == null) { out.nullValue(); return }
                out.beginArray()
                value.toList().forEach { element.write(out, it) }
                out.endArray()
            }

            override fun read(reader: JsonReader): SnapshotStateList<AACCell>? {
                if (reader.peek() == JsonToken.NULL) { reader.nextNull(); return null }
                val list = mutableStateListOf<AACCell>()
                reader.beginArray()
                while (reader.hasNext()) element.read(reader)?.let { list.add(it) }
                reader.endArray()
                return list
            }
        }
        @Suppress("UNCHECKED_CAST")
        return adapter as TypeAdapter<T>
    }
}

/**
 * Gson ignore les valeurs par défaut Kotlin : les pictos enregistrés avant l'ajout de l'id
 * reviennent avec id = null (et parfois content = null). On les complète ici, récursivement.
 */
@Suppress("USELESS_CAST")
fun normalizeCells(cells: List<AACCell>?): List<AACCell> = (cells ?: emptyList()).map { cell ->
    val children = normalizeCells(cell.content as List<AACCell>?)
    cell.copy(
        id = (cell.id as String?) ?: newCellId(),
        content = mutableStateListOf<AACCell>().apply { addAll(children) }
    )
}
