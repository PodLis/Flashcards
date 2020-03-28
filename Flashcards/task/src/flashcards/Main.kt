package flashcards

import java.io.File
import java.util.Scanner

class ExitException : Exception()

class Flashcards (outStream: (String) -> Unit, inStream: () -> String) {

    data class Card (val term: String, val def: String)

    private val cards = mutableMapOf<Card, Int>()
    private val logs = mutableListOf<String>()
    private var startFile: File? = null
    private var endFile: File? = null

    private val outStream = { output: String ->
        logs.add(output)
        outStream(output)
    }

    private val inStream = {
        val input = inStream()
        if (input == "exit") {
            this.outStream("Bye bye!")
            val x = endFile
            if (x != null) {
                this.outStream("${exportFile(x)} cards have been saved.")
            }
            throw ExitException()
        }
        logs.add("> $input")
        input
    }

    constructor(outStream: (String) -> Unit, inStream: () -> String, args: Array<String>) : this(outStream, inStream) {
        for (i in args.indices) {
            if (args[i].startsWith("-") && i < args.lastIndex && !args[i + 1].startsWith("-")) {
                when (args[i].drop(1)) {
                    "import" -> startFile = File(args[i + 1])
                    "export" -> endFile = File(args[i + 1])
                }
            }
        }
    }

    fun startFlow() {
        var command: String
        val x = startFile
        if (x != null)
            outStream("${importFile(x)} cards have been loaded.")
        try {
            loop@ do {
                outStream("Input the action (add, remove, import, export, ask, exit, log, hardest card, reset stats):")
                command = inStream()
                when (command) {
                    "add" -> addCard()
                    "remove" -> removeCard()
                    "import" -> {
                        outStream("File name:")
                        val file = File(inStream())
                        outStream(
                                if (file.exists())
                                    "${importFile(file)} cards have been loaded."
                                else
                                    "File not found."
                        )
                    }
                    "export" -> {
                        outStream("File name:")
                        outStream("${exportFile(File(inStream()))} cards have been saved.")
                    }
                    "log" -> {
                        outStream("File name:")
                        logToFile(File(inStream()))
                        outStream("The log has been saved.")
                    }
                    "ask" -> {
                        outStream("How many times to ask?")
                        takeQuiz(inStream().toInt())
                    }
                    "hardest card" -> showHardestCards()
                    "reset stats" -> resetStats()
                }
                outStream("")
            } while (true)
        } catch (e: ExitException) {}
    }

    private fun addCard() {
        outStream("The card:")
        val term = inStream()

        if (containsTerm(term)) {
            outStream("The card \"$term\" already exists.")
            return
        }

        outStream("The definition of the card:")
        val def = inStream()

        if (containsDef(def)) {
            outStream("The definition \"$def\" already exists.")
            return
        }

        cards[Card(term, def)] = 0
        outStream("The pair (\"$term\":\"$def\") has been added.")
    }

    private fun removeCard() {
        outStream("The card:")
        val term = inStream()
        if (containsTerm(term)) {
            removeCardByTerm(term)
            outStream("The card has been removed.")
        } else {
            outStream("Can't remove \"$term\": there is no such card.")
        }
    }

    private fun importFile(file: File): Int {
        var n = 0
        file.forEachLine {
            val list = it.split(" :: ")
            if (list.size == 3) {
                removeCardByTerm(list[0])
                cards[Card(list[0], list[1])] = list[2].toInt()
                n += 1
            }
        }
        return n
    }

    private fun exportFile(file: File): Int {
        var text = ""
        var n = 0
        for (card in cards) {
            text += card.key.term + " :: " + card.key.def + " :: " + card.value + "\n"
            n += 1
        }
        file.writeText(text)
        return n
    }

    private fun logToFile(file: File) {
        var text = ""
        for (log in logs)
            text += log + "\n"
        file.writeText(text)
    }

    private fun takeQuiz(n: Int) {
        if (cards.isEmpty())
            return outStream("Create a card first")
        repeat(n / cards.size) {
            for (card in cards)
                cards[card.key] = card.value + askCard(card.key)
        }
        var i = 0
        for (card in cards) {
            i += 1
            if (i > n % cards.size) return
            cards[card.key] = card.value + askCard(card.key)
        }
    }

    private fun askCard(card: Card): Int {
        outStream("Print the definition of \"${card.term}\":")
        val answer = inStream()
        if (answer == card.def) {
            outStream("Correct answer.")
            return 0
        }
        if (containsDef(answer)) {
            outStream("Wrong answer. The correct one is \"${card.def}\", " +
                    "you've just written the definition of \"${getTermFromDef(answer)}\".")
        }
        else
            outStream("Wrong answer. The correct one is \"${card.def}\".")
        return 1
    }

    private fun getTermFromDef(def: String): String? {
        for (key in (cards.filterKeys { it.def == def }).keys)
            return key.term
        return null
    }

    private fun showHardestCards() {
        var max = 0
        var badBoys = mutableSetOf<String>()
        for (card in cards) {
            if (max != 0 && card.value == max) {
                badBoys.add(card.key.term)
            }
            if (card.value > max) {
                max = card.value
                badBoys = mutableSetOf(card.key.term)
            }
        }
        outStream( when (badBoys.size) {
            0 -> "There are no cards with errors."
            1 -> "The hardest card is \"${badBoys.single()}\". You have $max errors answering it."
            else -> {
                var string = ""
                for (s in badBoys)
                    string += "\"$s\", "
                "The hardest cards are ${string.dropLast(2)}. You have $max errors answering them."
            }
        })
    }

    private fun resetStats() {
        for (card in cards) {
            cards[card.key] = 0
        }
        outStream("Card statistics has been reset.")
    }

    private fun containsTerm(term: String): Boolean {
        for (card in cards.keys)
            if (card.term == term) return true
        return false
    }

    private fun containsDef(def: String): Boolean {
        for (card in cards.keys)
            if (card.def == def) return true
        return false
    }

    private fun removeCardByTerm(term: String) {
        cards.remove(getCardByTerm(term))
    }

    private fun getCardByTerm(term: String): Card? {
        for (card in cards.keys)
            if (card.term == term) return card
        return null
    }

}

fun main(args: Array<String>) {
    val scanner = Scanner(System.`in`)
    val flashcards = if (args.isEmpty())
        Flashcards({ println(it) }, { scanner.nextLine() }) else
        Flashcards({ println(it) }, { scanner.nextLine() }, args)
    flashcards.startFlow()
}
