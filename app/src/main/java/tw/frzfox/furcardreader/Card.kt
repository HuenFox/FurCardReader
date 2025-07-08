package tw.frzfox.furcardreader

data class Card(var cardType : String,
    var cardUID : String,
    var cardATQA : String,
    var cardSAK : String,
    var maxTransLen : Int,
    var timeout : Int,
    ) {
}