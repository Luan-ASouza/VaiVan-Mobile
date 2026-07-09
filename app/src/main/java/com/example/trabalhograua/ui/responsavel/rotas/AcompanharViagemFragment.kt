package com.example.trabalhograua

import android.graphics.Color
import android.os.Bundle
import android.util.Xml
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import org.xmlpull.v1.XmlPullParser

class AcompanharViagemFragment : Fragment(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Infla o layout exclusivo do fragmento do mapa
        return inflater.inflate(R.layout.fragment_acompanhar_viagem, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Como estamos dentro de um Fragment, usamos childFragmentManager para encontrar o mapa
        val mapFragment = childFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment?

        mapFragment?.getMapAsync(this)
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
            if (event == XmlPullParser.START_TAG && parser.name == "trkpt") {
                val lat = parser.getAttributeValue(null, "lat").toDouble()
                val lon = parser.getAttributeValue(null, "lon").toDouble()
                pontos.add(LatLng(lat, lon))
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

            mMap.addMarker(MarkerOptions().position(pontos.first()).title("Início da rota"))
            mMap.addMarker(MarkerOptions().position(pontos.last()).title("Destino"))
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(pontos.first(), 14f))
        }
    }
}