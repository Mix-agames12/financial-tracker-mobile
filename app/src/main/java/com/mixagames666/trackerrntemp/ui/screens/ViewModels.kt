package com.mixagames666.trackerrntemp.ui.screens

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mixagames666.trackerrntemp.AppContainer

/** ViewModel de pantalla creado con el contenedor de dependencias de la app. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(container: AppContainer, crossinline create: (AppContainer) -> VM): VM =
    viewModel(factory = viewModelFactory { initializer { create(container) } })
