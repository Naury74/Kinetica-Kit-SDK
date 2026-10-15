package com.example.consumer.xml

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.consumer.R
import com.example.consumer.SampleScene
import com.naury.kinetica.board.BoardStates
import com.naury.kinetica.board.BoardStorage
import com.naury.kinetica.board.BoardWidget
import com.naury.kinetica.board.KineticaBoardState
import com.naury.kinetica.board.KineticaBoardView
import com.naury.kinetica.player.KineticaPlayerView
import com.naury.kinetica.presets.EffectPresets
import com.naury.kinetica.render.ViewEffects
import com.naury.kinetica.ui.view.GlassDialogs
import com.naury.kinetica.ui.view.GlassPopupHandle
import com.naury.kinetica.ui.view.GlassPopupOptions
import com.naury.kinetica.ui.view.GlassSheets
import com.naury.kinetica.ui.view.GlassToasts

/** AppCompat + CoordinatorLayout + Fragment 두 개로 만든 XML 호스트 화면. Navigation component 없이 FragmentManager 뒤로 가기 스택을 쓴다. */
class XmlHostActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_xml_host)
        if (savedInstanceState == null) {
            supportFragmentManager.commit { replace(R.id.container, ListFragment()) }
        }
    }

    fun openDetail(position: Int) {
        supportFragmentManager.commit {
            replace(R.id.container, DetailFragment().apply { arguments = bundleOf("position" to position) })
            addToBackStack("detail")
        }
    }
}

/** RecyclerView 항목마다 GlassSurfaceView와 ViewEffects를 붙인 목록. */
class ListFragment : Fragment(R.layout.fragment_list) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val last = view.findViewById<TextView>(R.id.lastClicked)
        val recycler = view.findViewById<RecyclerView>(R.id.recycler)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = RowAdapter(
            onClick = { last.text = "누른 항목: $it" },
            onOpen = { (requireActivity() as XmlHostActivity).openDetail(it) },
        )
    }
}

private class RowAdapter(private val onClick: (Int) -> Unit, private val onOpen: (Int) -> Unit) : RecyclerView.Adapter<RowAdapter.Holder>() {
    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val glass: View = view.findViewById(R.id.glass)
        val title: TextView = view.findViewById(R.id.title)
        val open: Button = view.findViewById(R.id.open)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_row, parent, false))

    override fun getItemCount() = ROWS

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.title.text = "행 $position"
        holder.glass.contentDescription = "행 $position"
        holder.glass.setOnClickListener { onClick(holder.bindingAdapterPosition) }
        holder.open.setOnClickListener { onOpen(holder.bindingAdapterPosition) }
        // 재사용된 View에도 매번 다시 붙인다. apply는 이전 효과를 지우고 새로 붙인다.
        ViewEffects.apply(holder.glass, if (position % 2 == 0) EffectPresets.button else EffectPresets.listItem(position), 16f)
    }

    companion object {
        const val ROWS = 60
    }
}

/** ViewPager2 안에 보드와 재생기를 두고, Fragment에서 글래스 시트, 다이얼로그, 토스트를 부르는 화면. */
class DetailFragment : Fragment(R.layout.fragment_detail) {
    private var sheet: GlassPopupHandle? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val position = requireArguments().getInt("position")
        val event = view.findViewById<TextView>(R.id.detailEvent)
        view.findViewById<TextView>(R.id.detailTitle).text = "상세 $position"
        // 문서(docs/integration.md)대로 Fragment를 그대로 넘긴다.
        val board = BoardStates.obtain(this, CATALOG, BoardStorage.sharedPreferences(requireContext(), "xml-host", "board"))
        view.findViewById<ViewPager2>(R.id.pager).adapter = PageAdapter(board) { event.text = "이벤트: $it" }

        view.findViewById<Button>(R.id.openSheet).setOnClickListener {
            val handle = GlassSheets.show(requireActivity(), R.layout.sheet_form, GlassPopupOptions(title = "메모", lifecycleOwner = viewLifecycleOwner))
            handle.contentView?.findViewById<Button>(R.id.sheetClose)?.setOnClickListener {
                event.text = "이벤트: 시트 저장 " + handle.contentView?.findViewById<EditText>(R.id.sheetInput)?.text
                handle.dismiss()
            }
            handle.setOnDismissListener { if (sheet === handle) sheet = null }
            sheet = handle
        }
        view.findViewById<Button>(R.id.openDialog).setOnClickListener {
            val body = TextView(requireContext()).apply { text = "XML 다이얼로그 내용"; setPadding(48, 48, 48, 48) }
            GlassDialogs.show(requireActivity(), body, GlassPopupOptions(title = "확인", lifecycleOwner = viewLifecycleOwner))
        }
        view.findViewById<Button>(R.id.showToast).setOnClickListener { GlassToasts.show(requireActivity(), "XML 토스트") }
    }

    companion object {
        val CATALOG = listOf(BoardWidget("hello", "인사", widthDp = 170f, heightDp = 100f), BoardWidget("news", "소식", widthDp = 170f, heightDp = 100f))
    }
}

private class PageAdapter(private val board: KineticaBoardState, private val onEvent: (String) -> Unit) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun getItemCount() = 2

    override fun getItemViewType(position: Int) = position

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = if (viewType == 0) {
            (inflater.inflate(R.layout.page_board, parent, false) as KineticaBoardView).apply {
                setBoard(board) { id, context ->
                    Button(context).apply {
                        text = "위젯 ${board.widgetKeyOf(id)}"
                        setOnClickListener { onEvent("위젯 ${board.widgetKeyOf(id)}") }
                    }
                }
                setOnLongPressListener { isEditing = true; onEvent("편집 시작") }
            }
        } else {
            (inflater.inflate(R.layout.page_player, parent, false) as KineticaPlayerView).apply {
                setDocument(SampleScene.document)
                contentDescription = "재생기"
            }
        }
        return object : RecyclerView.ViewHolder(view) {}
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
}
