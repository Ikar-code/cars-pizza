package fr.btssio.carspizza.voice

import fr.btssio.carspizza.voice.VoiceCommand.Add
import fr.btssio.carspizza.voice.VoiceCommand.Ambiguous
import fr.btssio.carspizza.voice.VoiceCommand.CancelOrder
import fr.btssio.carspizza.voice.VoiceCommand.Confirm
import fr.btssio.carspizza.voice.VoiceCommand.ReadBack
import fr.btssio.carspizza.voice.VoiceCommand.Remove
import fr.btssio.carspizza.voice.VoiceCommand.Unknown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandParserTest {

    // Menu de départ de la pizzeria (assets/db/seed.sql), sans le mot « pizza » dans les noms.
    private val parser = CommandParser(
        listOf(
            MenuEntry(1, "Margherita"),
            MenuEntry(2, "Reine"),
            MenuEntry(3, "Napolitaine"),
            MenuEntry(4, "Calzone"),
            MenuEntry(5, "Végétarienne"),
            MenuEntry(6, "Orientale"),
            MenuEntry(7, "Chèvre miel"),
            MenuEntry(8, "Savoyarde"),
        )
    )

    @Test fun `plusieurs pizzas dans une phrase`() {
        assertEquals(
            listOf(Add(1, "Margherita", 2), Add(2, "Reine", 1)),
            parser.parse("deux margherita et une reine"),
        )
    }

    @Test fun `formule de politesse et mot pizza`() {
        assertEquals(
            listOf(Add(1, "Margherita", 1)),
            parser.parse("Je voudrais une pizza margherita s'il vous plaît"),
        )
    }

    @Test fun `quantite en chiffres`() {
        assertEquals(listOf(Add(7, "Chèvre miel", 2)), parser.parse("2 chèvre miel"))
    }

    @Test fun `pluriel et accents`() {
        assertEquals(listOf(Add(2, "Reine", 3)), parser.parse("trois reines"))
        assertEquals(listOf(Add(5, "Végétarienne", 1)), parser.parse("une vegetarienne"))
    }

    @Test fun `faute de reconnaissance tolérée`() {
        assertEquals(listOf(Add(1, "Margherita", 1)), parser.parse("une pizza margarita"))
        assertEquals(listOf(Add(4, "Calzone", 1)), parser.parse("une calzone"))
    }

    @Test fun `mot partiel unique`() {
        assertEquals(listOf(Add(7, "Chèvre miel", 1)), parser.parse("une chèvre"))
    }

    @Test fun `pizza seule demande de preciser`() {
        val result = parser.parse("une pizza")
        assertEquals(1, result.size)
        val ambiguous = result.first() as Ambiguous
        assertEquals(8, ambiguous.options.size)
        assertTrue("Margherita" in ambiguous.options)
    }

    @Test fun `retrait`() {
        assertEquals(listOf(Remove(2, "Reine", null)), parser.parse("enlève la reine"))
        assertEquals(listOf(Remove(1, "Margherita", 1)), parser.parse("retire une margherita"))
    }

    @Test fun `ajout puis retrait dans la meme phrase`() {
        assertEquals(
            listOf(Add(4, "Calzone", 1), Remove(2, "Reine", null)),
            parser.parse("ajoute une calzone et enlève la reine"),
        )
    }

    @Test fun `validation`() {
        assertEquals(listOf(Confirm), parser.parse("valide la commande"))
        assertEquals(listOf(Add(1, "Margherita", 2), Confirm), parser.parse("deux margherita c'est tout"))
    }

    @Test fun `annulation`() {
        assertEquals(listOf(CancelOrder), parser.parse("annule la commande"))
        assertEquals(listOf(CancelOrder), parser.parse("Annuler toute la commande"))
    }

    @Test fun `annule une pizza n est pas une annulation de commande`() {
        assertEquals(listOf(Remove(2, "Reine", null)), parser.parse("annule la reine"))
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
        val result = parser.parseBest(listOf("un hippopotame", "une reine"))
        assertEquals(listOf(Add(2, "Reine", 1)), result)
    }

    // ---- resolve : nom isolé envoyé par un appel d'outil Gemini ----

    @Test fun `resolve trouve une pizza`() {
        assertEquals(MenuMatch.Found(MenuEntry(1, "Margherita")), parser.resolve("margarita"))
        assertEquals(MenuMatch.Found(MenuEntry(2, "Reine")), parser.resolve("la reine"))
    }

    @Test fun `resolve pizza inconnue`() {
        assertEquals(MenuMatch.NotFound, parser.resolve("hawaïenne ananas"))
        assertEquals(MenuMatch.NotFound, parser.resolve("   "))
    }

    @Test fun `resolve nom ambigu`() {
        val ambiguous = parser.resolve("pizza") as MenuMatch.Ambiguous
        assertEquals(8, ambiguous.names.size)
    }

    @Test fun `normalisation`() {
        assertEquals("jus d orange", CommandParser.normalize("Jus d'Orange !"))
        assertEquals("gateau", CommandParser.stem("gateaux"))
    }
}
