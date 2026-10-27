package com.prirai.android.nira.settings.fragment

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.prirai.android.nira.settings.NiraBarsPreviewPreference
import androidx.preference.SeekBarPreference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.prirai.android.nira.R
import com.prirai.android.nira.preferences.UserPreferences
import com.prirai.android.nira.settings.HomepageBackgroundChoice
import com.prirai.android.nira.theme.applyAppTheme


class CustomizationSettingsFragment : BaseSettingsFragment() {

    private lateinit var getBackgroundImageUri: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        getBackgroundImageUri =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val uri = result.data?.data
                    // Handle the returned Uri
                    if (uri != null) {
                        requireContext().contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        UserPreferences(requireContext()).homepageBackgroundUrl = uri.toString()
                        Toast.makeText(
                            context,
                            requireContext().resources.getText(R.string.successful),
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        UserPreferences(requireContext()).homepageBackgroundChoice =
                            HomepageBackgroundChoice.NONE.ordinal
                        Toast.makeText(
                            context,
                            requireContext().resources.getText(R.string.failed),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, s: String?) {
        addPreferencesFromResource(R.xml.preferences_customization)

        val niraPrefs = UserPreferences(requireContext())
        // The preview is a live representation of both bars, placed above the sliders.
        val barsPreview = NiraBarsPreviewPreference(requireContext()).apply {
            key = "nira_bars_live_preview"
            order = 0
            setHeights(niraPrefs.niraAddressBarHeightDp, niraPrefs.niraTabStripHeightDp)
        }
        preferenceScreen.findPreference<PreferenceCategory>("nira_toolbar_nav_section")
            ?.addPreference(barsPreview)
        preferenceScreen.findPreference<SeekBarPreference>("nira_address_height_dp")?.apply {
            value = niraPrefs.niraAddressBarHeightDp
            setOnPreferenceChangeListener { _, updated ->
                niraPrefs.niraAddressBarHeightDp = (updated as Number).toInt()
                barsPreview.setHeights(niraPrefs.niraAddressBarHeightDp, niraPrefs.niraTabStripHeightDp)
                true
            }
        }
        preferenceScreen.findPreference<SeekBarPreference>("nira_tab_strip_height_dp")?.apply {
            value = niraPrefs.niraTabStripHeightDp
            setOnPreferenceChangeListener { _, updated ->
                niraPrefs.niraTabStripHeightDp = (updated as Number).toInt()
                barsPreview.setHeights(niraPrefs.niraAddressBarHeightDp, niraPrefs.niraTabStripHeightDp)
                true
            }
        }
        preferenceScreen.findPreference<SeekBarPreference>("nira_tab_circle_size_dp")?.apply {
            value = niraPrefs.niraTabCircleSizeDp.coerceIn(10, 52)
            setOnPreferenceChangeListener { _, updated ->
                niraPrefs.niraTabCircleSizeDp = (updated as Number).toInt().coerceIn(10, 52)
                true
            }
        }
        preferenceScreen.findPreference<SeekBarPreference>("nira_tab_circle_gap_dp")?.apply {
            value = niraPrefs.niraTabCircleGapDp.coerceIn(-6, 40)
            setOnPreferenceChangeListener { _, updated ->
                niraPrefs.niraTabCircleGapDp = (updated as Number).toInt().coerceIn(-6, 40)
                true
            }
        }
        preferenceScreen.findPreference<Preference>("nira_restore_bar_heights")?.setOnPreferenceClickListener {
            niraPrefs.niraAddressBarHeightDp = 56
            niraPrefs.niraTabStripHeightDp = 48
            barsPreview.setHeights(56, 48)
            preferenceScreen.findPreference<SeekBarPreference>("nira_address_height_dp")?.value = 56
            preferenceScreen.findPreference<SeekBarPreference>("nira_tab_strip_height_dp")?.value = 48
            true
        }
        switchPreference(
            preference = "nira_group_chips_visible",
            isChecked = niraPrefs.niraGroupChipsVisible,
            onCheckChange = {
                niraPrefs.niraGroupChipsVisible = it
                Toast.makeText(requireContext(), getString(R.string.app_restart), Toast.LENGTH_SHORT).show()
            }
        )
        switchPreference(
            preference = "nira_group_bar_bottom",
            isChecked = niraPrefs.niraGroupBarBottom,
            onCheckChange = {
                niraPrefs.niraGroupBarBottom = it
                Toast.makeText(requireContext(), getString(R.string.app_restart), Toast.LENGTH_SHORT).show()
            }
        )
        preferenceScreen.findPreference<Preference>("nira_toolbar_config_portrait")
            ?.setOnPreferenceClickListener { showToolbarActionDialog(false); true }
        preferenceScreen.findPreference<Preference>("nira_toolbar_config_landscape")
            ?.setOnPreferenceClickListener { showToolbarActionDialog(true); true }

        preferenceScreen.findPreference<SeekBarPreference>("nira_toolbar_action_slot_dp")?.apply {
            value = niraPrefs.niraToolbarActionSlotDp
            setOnPreferenceChangeListener { _, updated ->
                niraPrefs.niraToolbarActionSlotDp = (updated as Number).toInt()
                true
            }
        }
        preferenceScreen.findPreference<SeekBarPreference>("nira_address_min_width_dp")?.apply {
            value = niraPrefs.niraAddressMinWidthDp
            setOnPreferenceChangeListener { _, updated ->
                niraPrefs.niraAddressMinWidthDp = (updated as Number).toInt()
                true
            }
        }
        switchPreference(
            preference = "nira_edge_panels_enabled",
            isChecked = niraPrefs.niraEdgePanelsEnabled,
            onCheckChange = { niraPrefs.niraEdgePanelsEnabled = it }
        )
        preferenceScreen.findPreference<SeekBarPreference>("nira_edge_width_dp")?.apply {
            value = niraPrefs.niraEdgeWidthDp
            setOnPreferenceChangeListener { _, updated ->
                niraPrefs.niraEdgeWidthDp = (updated as Number).toInt()
                true
            }
        }

        // Dynamic colors preference
        switchPreference(
            preference = "dynamic_colors",
            isChecked = UserPreferences(requireContext()).dynamicColors,
            onCheckChange = {
                UserPreferences(requireContext()).dynamicColors = it
                // Apply dynamic colors immediately
                if (it && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    com.google.android.material.color.DynamicColors.applyToActivitiesIfAvailable(requireActivity().application)
                }
                // Recreate activity to apply changes
                requireActivity().recreate()
            }
        )

        // AMOLED mode preference
        switchPreference(
            preference = "amoled_mode",
            isChecked = UserPreferences(requireContext()).amoledMode,
            onCheckChange = {
                UserPreferences(requireContext()).amoledMode = it
                // Show toast to restart app
                Toast.makeText(
                    requireContext(),
                    getString(R.string.app_restart),
                    Toast.LENGTH_LONG
                ).show()
            }
        )

        switchPreference(
            preference = requireContext().resources.getString(R.string.key_move_navbar),
            isChecked = !UserPreferences(requireContext()).shouldUseBottomToolbar,
            onCheckChange = {
                UserPreferences(requireContext()).shouldUseBottomToolbar = !it
            }
        )

        switchPreference(
            preference = requireContext().resources.getString(R.string.key_swipe_refresh),
            isChecked = UserPreferences(requireContext()).swipeToRefresh,
            onCheckChange = {
                UserPreferences(requireContext()).swipeToRefresh = it
                Toast.makeText(
                    context,
                    requireContext().resources.getText(R.string.app_restart),
                    Toast.LENGTH_LONG
                ).show()
            }
        )


        switchPreference(
            preference = requireContext().resources.getString(R.string.key_show_protocol),
            isChecked = UserPreferences(requireContext()).showUrlProtocol,
            onCheckChange = {
                UserPreferences(requireContext()).showUrlProtocol = it
                Toast.makeText(
                    context,
                    requireContext().resources.getText(R.string.app_restart),
                    Toast.LENGTH_LONG
                ).show()
            }
        )


        switchPreference(
            preference = requireContext().resources.getString(R.string.key_shortcuts_visible),
            isChecked = UserPreferences(requireContext()).showShortcuts,
            onCheckChange = {
                UserPreferences(requireContext()).showShortcuts = it
                Toast.makeText(
                    context,
                    requireContext().resources.getText(R.string.app_restart),
                    Toast.LENGTH_LONG
                ).show()
            }
        )

        switchPreference(
            preference = requireContext().resources.getString(R.string.key_stack_from_bottom),
            isChecked = UserPreferences(requireContext()).stackFromBottom,
            onCheckChange = {
                UserPreferences(requireContext()).stackFromBottom = it
                Toast.makeText(
                    context,
                    requireContext().resources.getText(R.string.app_restart),
                    Toast.LENGTH_LONG
                ).show()
            }
        )

        switchPreference(
            preference = requireContext().resources.getString(R.string.key_load_shortcut_icons),
            isChecked = UserPreferences(requireContext()).loadShortcutIcons,
            onCheckChange = {
                UserPreferences(requireContext()).loadShortcutIcons = it
            }
        )

        clickablePreference(
            preference = requireContext().resources.getString(R.string.key_app_theme_type),
            onClick = { pickAppTheme() }
        )

        clickablePreference(
            preference = requireContext().resources.getString(R.string.key_web_theme_type),
            onClick = { pickWebTheme() }
        )

        clickablePreference(
            preference = requireContext().resources.getString(R.string.key_homepage_background_image),
            onClick = { pickHomepageBackground() },
            summary = resources.getStringArray(R.array.homepage_background_image_types)[UserPreferences(
                requireContext()
            ).homepageBackgroundChoice]
        )

        switchPreference(
            preference = requireContext().resources.getString(R.string.key_auto_font_size),
            isChecked = UserPreferences(requireContext()).autoFontSize,
            onCheckChange = {
                UserPreferences(requireContext()).autoFontSize = it
                preferenceScreen.findPreference<SeekBarPreference>(resources.getString(R.string.key_font_size_factor))!!.isEnabled =
                    !it
                Toast.makeText(
                    context,
                    requireContext().resources.getText(R.string.app_restart),
                    Toast.LENGTH_LONG
                ).show()
            }
        )

        seekbarPreference(
            preference = requireContext().resources.getString(R.string.key_font_size_factor),
            isEnabled = !UserPreferences(requireContext()).autoFontSize,
            onStateChanged = {
                UserPreferences(requireContext()).fontSizeFactor = it.toFloat() / 100
                Toast.makeText(
                    context,
                    requireContext().resources.getText(R.string.app_restart),
                    Toast.LENGTH_LONG
                ).show()
            }
        )

        clickablePreference(
            preference = "toolbar_icon_size",
            onClick = { showIconSizeDialog() }
        )

        clickablePreference(
            preference = "interface_font_scale",
            onClick = { showFontScaleDialog() }
        )

        switchPreference(
            preference = requireContext().resources.getString(R.string.key_show_contextual_toolbar),
            isChecked = UserPreferences(requireContext()).showContextualToolbar,
            onCheckChange = {
                UserPreferences(requireContext()).showContextualToolbar = it
                Toast.makeText(
                    context,
                    requireContext().resources.getText(R.string.app_restart),
                    Toast.LENGTH_LONG
                ).show()
            }
        )

        switchPreference(
            preference = requireContext().resources.getString(R.string.key_show_tab_group_bar),
            isChecked = UserPreferences(requireContext()).showTabGroupBar,
            onCheckChange = {
                UserPreferences(requireContext()).showTabGroupBar = it
                Toast.makeText(
                    context,
                    requireContext().resources.getText(R.string.app_restart),
                    Toast.LENGTH_LONG
                ).show()
            }
        )

    }

    private fun pickHomepageBackground() {
        // Simple dialog with three direct action options (matching enum order)
        val items = arrayOf(
            getString(R.string.none),
            getString(R.string.homepage_background_gallery),
            getString(R.string.homepage_background_url)
        )
        
        MaterialAlertDialogBuilder(requireActivity())
            .setTitle(resources.getString(R.string.homepage_background_image))
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        // None - immediate action
                        UserPreferences(requireContext()).homepageBackgroundChoice = HomepageBackgroundChoice.NONE.ordinal
                        updateHomepageBackgroundSummary()
                        Toast.makeText(
                            context,
                            requireContext().resources.getText(R.string.successful),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    1 -> {
                        // Choose from gallery - launch picker immediately
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "image/*"
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                        }
                        UserPreferences(requireContext()).homepageBackgroundChoice = HomepageBackgroundChoice.GALLERY.ordinal
                        getBackgroundImageUri.launch(intent)
                    }
                    2 -> {
                        // Enter URL - show input dialog immediately
                        showUrlInputDialog()
                    }
                }
            }
            .setNegativeButton(resources.getString(R.string.cancel), null)
            .show()
    }
    
    private fun updateHomepageBackgroundSummary() {
        val singleItems = resources.getStringArray(R.array.homepage_background_image_types)
        val currentChoice = UserPreferences(requireContext()).homepageBackgroundChoice
        preferenceScreen.findPreference<Preference>(
            requireContext().resources.getString(R.string.key_homepage_background_image)
        )?.summary = singleItems[currentChoice]
    }
    
    private fun showUrlInputDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edittext, null)
        val inputLayout = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.text_input_layout)
        val editText = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edit_text)
        
        inputLayout.hint = resources.getString(R.string.url)
        editText.setText(UserPreferences(requireContext()).homepageBackgroundUrl)
        
        MaterialAlertDialogBuilder(requireActivity())
            .setTitle(resources.getString(R.string.homepage_background_image))
            .setView(dialogView)
            .setPositiveButton(resources.getString(R.string.mozac_feature_prompts_ok)) { _, _ ->
                if (editText.text.toString().isNotEmpty()) {
                    UserPreferences(requireContext()).homepageBackgroundChoice = HomepageBackgroundChoice.URL.ordinal
                    UserPreferences(requireContext()).homepageBackgroundUrl = editText.text.toString()
                    updateHomepageBackgroundSummary()
                    Toast.makeText(
                        context,
                        requireContext().resources.getText(R.string.successful),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    UserPreferences(requireContext()).homepageBackgroundChoice = HomepageBackgroundChoice.NONE.ordinal
                    updateHomepageBackgroundSummary()
                    Toast.makeText(
                        context,
                        requireContext().resources.getText(R.string.failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(resources.getString(R.string.cancel), null)
            .show()
    }

    private fun pickAppTheme() {
        val items = resources.getStringArray(R.array.theme_types)
        var selectedChoice = UserPreferences(requireContext()).appThemeChoice

        MaterialAlertDialogBuilder(requireActivity())
            .setTitle(resources.getString(R.string.theme))
            .setSingleChoiceItems(items, selectedChoice) { _, which ->
                selectedChoice = which
            }
            .setNegativeButton(resources.getString(R.string.cancel), null)
            .setPositiveButton(resources.getString(R.string.mozac_feature_prompts_ok)) { _, _ ->
                UserPreferences(requireContext()).appThemeChoice = selectedChoice
                applyAppTheme(selectedChoice)
                requireActivity().recreate()
            }
            .show()
    }

    private fun showIconSizeDialog() {
        val userPreferences = UserPreferences(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.dialog_size_picker, null)
        val slider = dialogView.findViewById<com.google.android.material.slider.Slider>(R.id.sizeSlider)
        val previewIcon = dialogView.findViewById<android.widget.ImageView>(R.id.previewIcon)
        val previewText = dialogView.findViewById<android.widget.TextView>(R.id.previewText)
        
        slider.value = userPreferences.toolbarIconSize
        slider.valueFrom = 0.8f
        slider.valueTo = 1.5f
        slider.stepSize = 0.1f
        
        previewText.text = getString(R.string.toolbar_icon_size)
        updateIconPreview(previewIcon, userPreferences.toolbarIconSize)
        
        slider.addOnChangeListener { _, value, _ ->
            updateIconPreview(previewIcon, value)
        }
        
        MaterialAlertDialogBuilder(requireActivity())
            .setTitle(R.string.toolbar_icon_size)
            .setView(dialogView)
            .setPositiveButton(R.string.mozac_feature_prompts_ok) { _, _ ->
                userPreferences.toolbarIconSize = slider.value
                Toast.makeText(
                    context,
                    requireContext().resources.getText(R.string.app_restart),
                    Toast.LENGTH_LONG
                ).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun showFontScaleDialog() {
        val userPreferences = UserPreferences(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.dialog_size_picker, null)
        val slider = dialogView.findViewById<com.google.android.material.slider.Slider>(R.id.sizeSlider)
        val previewIcon = dialogView.findViewById<android.widget.ImageView>(R.id.previewIcon)
        val previewText = dialogView.findViewById<android.widget.TextView>(R.id.previewText)
        
        slider.value = userPreferences.interfaceFontScale
        slider.valueFrom = 0.8f
        slider.valueTo = 1.3f
        slider.stepSize = 0.1f
        
        previewIcon.visibility = android.view.View.GONE
        previewText.text = getString(R.string.interface_font_scale_preview)
        updateFontPreview(previewText, userPreferences.interfaceFontScale)
        
        slider.addOnChangeListener { _, value, _ ->
            updateFontPreview(previewText, value)
        }
        
        MaterialAlertDialogBuilder(requireActivity())
            .setTitle(R.string.interface_font_scale)
            .setView(dialogView)
            .setPositiveButton(R.string.mozac_feature_prompts_ok) { _, _ ->
                userPreferences.interfaceFontScale = slider.value
                Toast.makeText(
                    context,
                    requireContext().resources.getText(R.string.app_restart),
                    Toast.LENGTH_LONG
                ).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun updateIconPreview(icon: android.widget.ImageView, scale: Float) {
        val baseSize = (48 * resources.displayMetrics.density).toInt()
        val scaledSize = (baseSize * scale).toInt()
        val layoutParams = icon.layoutParams
        layoutParams.width = scaledSize
        layoutParams.height = scaledSize
        icon.layoutParams = layoutParams
    }
    
    private fun updateFontPreview(textView: android.widget.TextView, scale: Float) {
        val baseSize = 16f
        textView.textSize = baseSize * scale
    }

    private fun pickWebTheme() {
        val items = resources.getStringArray(R.array.theme_types)
        var selectedChoice = UserPreferences(requireContext()).webThemeChoice

        MaterialAlertDialogBuilder(requireActivity())
            .setTitle(resources.getString(R.string.web_theme))
            .setSingleChoiceItems(items, selectedChoice) { _, which ->
                selectedChoice = which
            }
            .setNegativeButton(resources.getString(R.string.cancel), null)
            .setPositiveButton(resources.getString(R.string.mozac_feature_prompts_ok)) { _, _ ->
                UserPreferences(requireContext()).webThemeChoice = selectedChoice
            }
            .show()
    }


    /** Saves each change immediately; the toolbar observes the same preferences live. */
    private fun showToolbarActionDialog(landscape: Boolean) {
        val ctx = requireContext()
        val prefs = UserPreferences(ctx)
        val config = com.prirai.android.nira.components.toolbar.ToolbarActionConfiguration
        val order = config.getOrder(prefs, landscape).toMutableList()
        val hidden = config.getHidden(prefs, landscape).toMutableSet()
        val dp = { value: Int -> (value * resources.displayMetrics.density).toInt() }

        val content = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }
        content.addView(android.widget.TextView(ctx).apply {
            text = "Przytrzymaj ↕ i przeciągnij wiersz, aby zmienić kolejność. " +
                "Zaznaczenie określa widoczność."
            setPadding(0, dp(4), 0, dp(10))
        })
        val rows = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }
        content.addView(rows)

        fun redrawRows() {
            rows.removeAllViews()
            order.forEach { id ->
                val row = android.widget.LinearLayout(ctx).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    minimumHeight = dp(46)
                    setPadding(dp(2), 0, dp(2), 0)
                }
                val handle = android.widget.TextView(ctx).apply {
                    text = "↕"
                    textSize = 23f
                    gravity = android.view.Gravity.CENTER
                    contentDescription = "Przeciągnij " + (config.labels[id] ?: id)
                    layoutParams = android.widget.LinearLayout.LayoutParams(dp(40), dp(46))
                    setOnLongClickListener {
                        row.startDragAndDrop(
                            android.content.ClipData.newPlainText("zavro-toolbar", id),
                            android.view.View.DragShadowBuilder(row), id, 0
                        )
                        true
                    }
                }
                row.setOnDragListener { _, event ->
                    when (event.action) {
                        android.view.DragEvent.ACTION_DRAG_STARTED -> event.localState is String
                        android.view.DragEvent.ACTION_DROP -> {
                            val moved = event.localState as? String
                            if (moved != null && moved in order && moved != id) {
                                order.remove(moved)
                                order.add(order.indexOf(id), moved)
                                config.storeOrder(prefs, landscape, order)
                                rows.post { redrawRows() }
                            }
                            true
                        }
                        android.view.DragEvent.ACTION_DRAG_ENDED -> true
                        else -> true
                    }
                }
                row.addView(handle)
                val toggle = android.widget.CheckBox(ctx).apply {
                    text = config.labels[id] ?: id
                    isChecked = id !in hidden
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) hidden.remove(id) else hidden.add(id)
                        config.storeHidden(prefs, landscape, hidden)
                    }
                }
                row.addView(toggle, android.widget.LinearLayout.LayoutParams(
                    0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                ))
                rows.addView(row)
            }
        }
        redrawRows()
        val amount = android.widget.TextView(ctx).apply {
            text = "Rozmiar ikon: ${config.getSize(prefs, landscape)} dp"
            setPadding(0, dp(12), 0, 0)
        }
        content.addView(amount)
        content.addView(android.widget.SeekBar(ctx).apply {
            max = 40 // 24–64 dp. Android touch targets stay >= 40 dp.
            progress = config.getSize(prefs, landscape) - 24
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean
                ) {
                    amount.text = "Rozmiar ikon: ${24 + progress} dp"
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                    seekBar?.let { config.storeSize(prefs, landscape, 24 + it.progress) }
                }
            })
        })
        val scroll = android.widget.ScrollView(ctx).apply { addView(content) }
        MaterialAlertDialogBuilder(ctx)
            .setTitle(if (landscape) "Przyciski paska — poziom" else "Przyciski paska — pion")
            .setView(scroll)
            .setPositiveButton("Gotowe", null)
            .setNeutralButton("Przywróć domyślne") { _, _ ->
                config.restore(prefs, landscape)
            }
            .show()
    }


}
