package com.example.cwc

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

class MapFragment : Fragment(), OnMapReadyCallback {

    private var mMap: GoogleMap? = null
    private lateinit var etSearch: EditText
    private lateinit var btnSearch: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_map, container, false)

        etSearch = view.findViewById(R.id.et_search)
        btnSearch = view.findViewById(R.id.btn_search)

        btnSearch.setOnClickListener {
            val query = etSearch.text.toString().trim()
            if (query.isNotEmpty()) {
                searchCoffeeShops(query)
            } else {
                Toast.makeText(requireContext(), "Enter a search term", Toast.LENGTH_SHORT).show()
            }
        }

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        return view
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        val israelCenter = LatLng(31.0461, 34.8516)
        mMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(israelCenter, 7f))
    }

    private fun searchCoffeeShops(query: String) {
        mMap?.clear()

        when {
            query.equals("aroma", ignoreCase = true) -> {
                val telAviv = LatLng(32.0853, 34.7818)
                val jerusalem = LatLng(31.7683, 35.2137)
                val haifa = LatLng(32.7940, 34.9896)
                val rishonLezion = LatLng(31.9714, 34.7925)

                mMap?.addMarker(MarkerOptions().position(telAviv).title("Aroma Tel Aviv"))
                mMap?.addMarker(MarkerOptions().position(jerusalem).title("Aroma Jerusalem"))
                mMap?.addMarker(MarkerOptions().position(haifa).title("Aroma Haifa"))
                mMap?.addMarker(MarkerOptions().position(rishonLezion).title("Aroma Rishon LeZion"))
                mMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(telAviv, 10f))
            }
            query.equals("cafe nero", ignoreCase = true) -> {
                val telAviv = LatLng(32.0853, 34.7818)
                val netanya = LatLng(32.3214, 34.8535)
                val beerSheva = LatLng(31.2518, 34.7913)
                val haifa = LatLng(32.7940, 34.9896)

                mMap?.addMarker(MarkerOptions().position(telAviv).title("Cafe Nero Tel Aviv"))
                mMap?.addMarker(MarkerOptions().position(netanya).title("Cafe Nero Netanya"))
                mMap?.addMarker(MarkerOptions().position(beerSheva).title("Cafe Nero Beer Sheva"))
                mMap?.addMarker(MarkerOptions().position(haifa).title("Cafe Nero Haifa"))
                mMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(telAviv, 10f))
            }
            query.equals("coffee bean", ignoreCase = true) -> {
                val telAviv = LatLng(32.0853, 34.7818)
                val jerusalem = LatLng(31.7683, 35.2137)
                val ramatGan = LatLng(32.0684, 34.8245)
                val haifa = LatLng(32.7940, 34.9896)

                mMap?.addMarker(MarkerOptions().position(telAviv).title("Coffee Bean Tel Aviv"))
                mMap?.addMarker(MarkerOptions().position(jerusalem).title("Coffee Bean Jerusalem"))
                mMap?.addMarker(MarkerOptions().position(ramatGan).title("Coffee Bean Ramat Gan"))
                mMap?.addMarker(MarkerOptions().position(haifa).title("Coffee Bean Haifa"))
                mMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(telAviv, 10f))
            }
            query.equals("cafe cafe", ignoreCase = true) -> {
                val telAviv = LatLng(32.0853, 34.7818)
                val rishonLezion = LatLng(31.9714, 34.7925)
                val netanya = LatLng(32.3214, 34.8535)
                val jerusalem = LatLng(31.7683, 35.2137)

                mMap?.addMarker(MarkerOptions().position(telAviv).title("Cafe Cafe Tel Aviv"))
                mMap?.addMarker(MarkerOptions().position(rishonLezion).title("Cafe Cafe Rishon LeZion"))
                mMap?.addMarker(MarkerOptions().position(netanya).title("Cafe Cafe Netanya"))
                mMap?.addMarker(MarkerOptions().position(jerusalem).title("Cafe Cafe Jerusalem"))
                mMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(telAviv, 10f))
            }
            query.equals("cofix", ignoreCase = true) -> {
                val telAviv = LatLng(32.0853, 34.7818)
                val jerusalem = LatLng(31.7683, 35.2137)
                val netanya = LatLng(32.3214, 34.8535)
                val beerSheva = LatLng(31.2518, 34.7913)

                mMap?.addMarker(MarkerOptions().position(telAviv).title("Cofix Tel Aviv"))
                mMap?.addMarker(MarkerOptions().position(jerusalem).title("Cofix Jerusalem"))
                mMap?.addMarker(MarkerOptions().position(netanya).title("Cofix Netanya"))
                mMap?.addMarker(MarkerOptions().position(beerSheva).title("Cofix Beer Sheva"))
                mMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(telAviv, 10f))
            }
            query.equals("gansipur", ignoreCase = true) -> {
                val telAviv = LatLng(32.0853, 34.7818)
                val jerusalem = LatLng(31.7683, 35.2137)
                val haifa = LatLng(32.7940, 34.9896)
                val netanya = LatLng(32.3214, 34.8535)

                mMap?.addMarker(MarkerOptions().position(telAviv).title("Gansipur Tel Aviv"))
                mMap?.addMarker(MarkerOptions().position(jerusalem).title("Gansipur Jerusalem"))
                mMap?.addMarker(MarkerOptions().position(haifa).title("Gansipur Haifa"))
                mMap?.addMarker(MarkerOptions().position(netanya).title("Gansipur Netanya"))
                mMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(telAviv, 10f))
            }
            else ->  {
                Toast.makeText(requireContext(), "No dummy data available for \"$query\"", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
