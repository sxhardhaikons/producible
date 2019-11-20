package com.sxhardha.producibledemo

import androidx.lifecycle.ViewModel
import com.sxhardha.producible.Producible
import retrofit2.Retrofit
import java.util.*

@Producible
class MainActivityViewModel(name: Retrofit, age: Calendar) : ViewModel() {
}