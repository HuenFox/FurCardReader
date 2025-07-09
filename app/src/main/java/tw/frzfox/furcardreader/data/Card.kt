package tw.frzfox.furcardreader.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Card(var cardType : String,
    var cardUID : String,
    var cardATQA : String,
    var cardSAK : String,
    var maxTransLen : Int,
    var timeout : Int,
    ) : Parcelable {
}