package com.example.trabalhograua

import android.graphics.Color
import android.util.Xml
import com.google.android.gms.maps.model.PolylineOptions
import org.xmlpull.v1.XmlPullParser
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

    }

    override fun onMapReady(googleMap: GoogleMap) {

        mMap = googleMap

        desenharRota()
    }

    private fun desenharRota() {

        val parser = Xml.newPullParser()

        parser.setInput(resources.openRawResource(R.raw.rota), "UTF-8")

        val pontos = mutableListOf<LatLng>()

        var event = parser.eventType

        while (event != XmlPullParser.END_DOCUMENT) {

            if (event == XmlPullParser.START_TAG &&
                parser.name == "trkpt") {

                val lat = parser.getAttributeValue(null, "lat").toDouble()
                val lon = parser.getAttributeValue(null, "lon").toDouble()

                pontos.add(
                    LatLng(lat, lon)
                )
            }

            event = parser.next()
        }

        if (pontos.isNotEmpty()) {

            mMap.addPolyline(
                PolylineOptions()
                    .addAll(pontos)
                    .width(12f)
                    .color(Color.parseColor("#F6B12F"))
                    .geodesic(true)
            )

            mMap.addMarker(
                MarkerOptions()
                    .position(pontos.first())
                    .title("Início da rota")
            )

            mMap.addMarker(
                MarkerOptions()
                    .position(pontos.last())
                    .title("Destino")
            )

            mMap.moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                    pontos.first(),
                    14f
                )
            )
        }
    }
}