package fr.btssio.voxresto.voice

import fr.btssio.voxresto.voice.VoiceCommand.Add
import fr.btssio.voxresto.voice.VoiceCommand.Ambiguous
import fr.btssio.voxresto.voice.VoiceCommand.CancelOrder
import fr.btssio.voxresto.voice.VoiceCommand.Confirm
import fr.btssio.voxresto.voice.VoiceCommand.ReadBack
import fr.btssio.voxresto.voice.VoiceCommand.Remove
import fr.btssio.voxresto.voice.VoiceCommand.Unknown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandParserTest {

    private val parser = CommandParser(
        listOf(
            MenuEntry(1, "Samoussa", listOf("samosa")),
            MenuEntry(2, "Bonbon piment"),
            MenuEntry(3, "Pizza Margherita"),
            MenuEntry(4, "Pizza Reine"),
            MenuEntry(5, "Frites", listOf("portion de frites")),
            MenuEntry(6, "Coca", listOf("coca cola")),
            MenuEntry(7, "Jus de litchi"),
            MenuEntry(8, "Cari poulet", listOf("carry poulet", "cari")),
        )
    )

    @Test fun `plusieurs plats dans une phrase`() {
        assertEquals(
            listOf(Add(1, "Samoussa", 2), Add(6, "Coca", 1)),
            parser.parse("Deux samoussas et un coca"),
        )
    }

    @Test fun `formule de politesse et verbe`() {
        assertEquals(
            listOf(Add(3, "Pizza Margherita", 1)),
            parser.parse("Je voudrais une pizza margherita s'il vous plaît"),
        )
    }

    @Test fun `quantite en chiffres`() {
        assertEquals(listOf(Add(7, "Jus de litchi", 2)), parser.parse("2 jus de litchi"))
    }

    @Test fun `pluriel et accents`() {
        assertEquals(listOf(Add(5, "Frites", 3)), parser.parse("trois frites"))
        assertEquals(listOf(Add(2, "Bonbon piment", 3)), parser.parse("Trois bonbons piment"))
    }

    @Test fun `faute de reconnaissance tolérée`() {
        assertEquals(listOf(Add(3, "Pizza Margherita", 1)), parser.parse("une pizza margarita"))
        assertEquals(listOf(Add(1, "Samoussa", 1)), parser.parse("un samoussas"))
    }

    @Test fun `alias`() {
        assertEquals(listOf(Add(8, "Cari poulet", 1)), parser.parse("un carry poulet"))
    }

    @Test fun `mot partiel unique`() {
        assertEquals(listOf(Add(3, "Pizza Margherita", 1)), parser.parse("une margherita"))
    }

    @Test fun `mot partiel ambigu demande de preciser`() {
        val result = parser.parse("une pizza")
        assertEquals(listOf(Ambiguous("pizza", listOf("Pizza Margherita", "Pizza Reine"))), result)
    }

    @Test fun `retrait`() {
        assertEquals(listOf(Remove(6, "Coca", null)), parser.parse("enlève le coca"))
        assertEquals(listOf(Remove(1, "Samoussa", 1)), parser.parse("retire un samoussa"))
    }

    @Test fun `ajout puis retrait dans la meme phrase`() {
        assertEquals(
            listOf(Add(5, "Frites", 1), Remove(6, "Coca", null)),
            parser.parse("ajoute une frite et enlève le coca"),
        )
    }

    @Test fun `validation`() {
        assertEquals(listOf(Confirm), parser.parse("valide la commande"))
        assertEquals(listOf(Add(5, "Frites", 2), Confirm), parser.parse("deux frites c'est tout"))
    }

    @Test fun `annulation`() {
        assertEquals(listOf(CancelOrder), parser.parse("annule la commande"))
        assertEquals(listOf(CancelOrder), parser.parse("Annuler toute la commande"))
    }

    @Test fun `annule un plat n est pas une annulation de commande`() {
        assertEquals(listOf(Remove(6, "Coca", null)), parser.parse("annule le coca"))
    }

    @Test fun `recapitulatif`() {
        assertEquals(listOf(ReadBack), parser.parse("c'est combien"))
        assertEquals(listOf(ReadBack), parser.parse("fais-moi le récapitulatif"))
    }

    @Test fun `phrase incomprise`() {
        val result = parser.parse("un hippopotame")
        assertEquals(1, result.size)
        assertTrue(result.first() is Unknown)
    }

    @Test fun `phrase vide`() {
        assertTrue(parser.parse("   ").isEmpty())
    }

    @Test fun `parseBest choisit l'hypothese la mieux comprise`() {
        val result = parser.parseBest(listOf("un hippopotame", "un coca"))
        assertEquals(listOf(Add(6, "Coca", 1)), result)
    }

    @Test fun `normalisation`() {
        assertEquals("jus d orange", CommandParser.normalize("Jus d'Orange !"))
        assertEquals("gateau", CommandParser.stem("gateaux"))
    }
}
