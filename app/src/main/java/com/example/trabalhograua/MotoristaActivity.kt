package com.example.trabalhograua

import android.os.Bundle
import android.view.View
import android.widget.Button
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

class MotoristaActivity : BaseActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_motorista)

        val bottom = findViewById<View>(R.id.bottomNavigation)

        configurarBottomNavigation(
            bottom,
            R.id.navRotas
        )

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

        val local = LatLng(-30.0346, -51.2177)

        mMap.addMarker(
            MarkerOptions()
                .position(local)
                .title("Estudantes próximos")
        )

        mMap.moveCamera(
            CameraUpdateFactory.newLatLngZoom(local, 13f)
        )
    }
}