package com.example.trabalhograua

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

class EstudanteActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_estudante)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment

        mapFragment.getMapAsync(this)

        val btnCancelar = findViewById<Button>(R.id.btnCancelar)

        btnCancelar.setOnClickListener {
            finish()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {

        mMap = googleMap

        // Exemplo: Porto Alegre
        val local = LatLng(-30.0346, -51.2177)

        mMap.addMarker(
            MarkerOptions().position(local).title("Van encontrada")
        )

        mMap.moveCamera(
            CameraUpdateFactory.newLatLngZoom(local, 14f)
        )
    }
}