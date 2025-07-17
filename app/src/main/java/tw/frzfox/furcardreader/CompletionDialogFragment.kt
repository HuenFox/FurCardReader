package tw.frzfox.furcardreader

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window.FEATURE_NO_TITLE
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.ScaleAnimation

import androidx.fragment.app.DialogFragment
import tw.frzfox.furcardreader.data.Card
import tw.frzfox.furcardreader.databinding.DialogCompletionBinding

class CompletionDialogFragment : DialogFragment() {

    private lateinit var binding: DialogCompletionBinding
    private var card: Card? = null

    companion object {
        private const val ARG_CARD_DATA = "card_data"
        private var success = true
        fun newInstance(success: Boolean, card: Card?): CompletionDialogFragment {
            val fragment = CompletionDialogFragment()
            val args = Bundle()
            args.putParcelable(ARG_CARD_DATA, card) // Card 需要是 Parcelable
            fragment.arguments = args
            this.success = success
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            card = it.getParcelable(ARG_CARD_DATA)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // 移除 Dialog 的標題
        dialog?.window?.requestFeature(FEATURE_NO_TITLE)
        // 設定 Dialog 背景為透明，以便圓角和自訂背景生效
        dialog?.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        // Inflate the layout for this fragment
//        val view = inflater.inflate(R.layout.dialog_completion, container, false)
        binding = DialogCompletionBinding.inflate(layoutInflater)
        // 設定卡片資訊
        binding.completionTitle.text = "卡片驗證成功！"
        card?.let {
            binding.cardIdText.text = "ID: ${it.cardUID}"
            // 您可以根據需要顯示更多 Card 的資訊
        } ?: run {
            binding.cardIdText.visibility = View.GONE
        }

        // 播放進入動畫
        playEnterAnimation(binding.dialogContentView) // dialog_content_view 是您 Dialog XML 根佈局的 ID

        return binding.root
    }

    override fun onStart() {
        super.onStart()
        // 設定 Dialog 的寬高，您可以根據需要調整
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun playEnterAnimation(dialogView: View) {
        // 縮放動畫
        val scaleAnimation = ScaleAnimation(
            0.5f, 1.0f, // From X, To X
            0.5f, 1.0f, // From Y, To Y
            Animation.RELATIVE_TO_SELF, 0.5f, // Pivot X
            Animation.RELATIVE_TO_SELF, 0.5f  // Pivot Y
        ).apply {
            duration = 300 // 動畫時長 (毫秒)
            fillAfter = true
        }

        // 淡入動畫
        val alphaAnimation = AlphaAnimation(0.0f, 1.0f).apply {
            duration = 300 // 動畫時長 (毫秒)
            fillAfter = true
        }

        // 您可以組合動畫，或只使用一個
        // val animationSet = AnimationSet(true)
        // animationSet.addAnimation(scaleAnimation)
        // animationSet.addAnimation(alphaAnimation)
        // dialogView.startAnimation(animationSet)

        // 這裡我們簡單地使用縮放動畫
        dialogView.startAnimation(scaleAnimation)
    }

    // 如果您也想在關閉時有動畫，可以覆寫 dismiss() 方法，
    // 但這會稍微複雜一些，因為您需要在動畫結束後才真正關閉 Dialog。
    // 一個簡單的方式是在按鈕點擊時先播放動畫，然後延遲關閉。
}