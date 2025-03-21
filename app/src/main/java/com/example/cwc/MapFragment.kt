package com.example.cwc

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.*

class MapFragment : Fragment(), OnMapReadyCallback {

  private var mMap: GoogleMap? = null
  private lateinit var etSearch: AutoCompleteTextView
  private lateinit var btnMyLocation: FloatingActionButton
  private lateinit var fusedLocationClient: FusedLocationProviderClient

  companion object {
    private const val LOCATION_PERMISSION_REQUEST_CODE = 1
  }

  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    val view = inflater.inflate(R.layout.fragment_map, container, false)

    etSearch = view.findViewById(R.id.et_search)
    btnMyLocation = view.findViewById(R.id.btn_my_location)

    btnMyLocation.setOnClickListener {
      showMyLocation()
    }

    fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

    val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
    mapFragment.getMapAsync(this)

    val coffeeChains = listOf("Aroma", "Cofix", "Cafe Cafe", "Greg", "Landwer")
    val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, coffeeChains)
    etSearch.setAdapter(adapter)

    etSearch.setOnItemClickListener { _, _, position, _ ->
      val selected = adapter.getItem(position) ?: return@setOnItemClickListener
      searchCoffeeShops(selected)
    }

    return view
  }

  override fun onMapReady(googleMap: GoogleMap) {
    mMap = googleMap
    val rishonLezion = LatLng(31.9714, 34.7722)
    mMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(rishonLezion, 12f))
    mMap?.setOnMarkerClickListener { marker ->
      openCoffeeShopDetails(marker)
      true
    }
  }

  private fun showMyLocation() {
    if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
      != PackageManager.PERMISSION_GRANTED
    ) {
      ActivityCompat.requestPermissions(
        requireActivity(),
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
        LOCATION_PERMISSION_REQUEST_CODE
      )
    } else {
      fusedLocationClient.lastLocation.addOnSuccessListener { location ->
        if (location != null) {
          val myLatLng = LatLng(location.latitude, location.longitude)
          mMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(myLatLng, 15f))
          mMap?.addMarker(MarkerOptions().position(myLatLng).title("You are here"))
        } else {
          Toast.makeText(requireContext(), "Unable to retrieve current location", Toast.LENGTH_SHORT).show()
        }
      }.addOnFailureListener {
        Toast.makeText(requireContext(), "Error retrieving location", Toast.LENGTH_SHORT).show()
      }
    }
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<String>,
    grantResults: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
      if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        showMyLocation()
      } else {
        Toast.makeText(requireContext(), "Location permission is required to show your current location", Toast.LENGTH_SHORT).show()
      }
    }
  }

  private fun addCoffeeMarker(location: LatLng, title: String, description: String) {
    val marker = mMap?.addMarker(MarkerOptions().position(location).title(title))
    marker?.tag = description
  }

  private fun searchCoffeeShops(query: String) {
    mMap?.clear()

    val results = when (query.lowercase(Locale.ROOT)) {
      "aroma" -> listOf(
        Triple(LatLng(32.0708, 34.7941), "Aroma Tel Aviv", "קפה איכותי ואווירה נעימה."),
        Triple(LatLng(31.7685, 35.2137), "Aroma Jerusalem", "מקום מפגש פופולרי."),
        Triple(LatLng(32.7940, 34.9896), "Aroma Haifa", "נוף לים וקפה משובח."),
        Triple(LatLng(31.9714, 34.7722), "Aroma Rishon LeZion", "שירות מצוין ומיקום מרכזי.")
      )
      "cofix" -> listOf(
        Triple(LatLng(32.0633, 34.7725), "Cofix Tel Aviv", "קפה זול ואיכותי."),
        Triple(LatLng(31.7945, 35.2172), "Cofix Jerusalem", "חוויה עירונית."),
        Triple(LatLng(32.3334, 34.8575), "Cofix Netanya", "מקום קבוע לחובבי הקפה."),
        Triple(LatLng(31.2520, 34.7915), "Cofix Beer Sheva", "קפה במחיר משתלם.")
      )
      "cafe cafe" -> listOf(
        Triple(LatLng(32.0764, 34.7805), "Cafe Cafe Tel Aviv", "מרפסת רחוב תוססת."),
        Triple(LatLng(31.9635, 34.7896), "Cafe Cafe Rishon LeZion", "מרכז עזריאלי."),
        Triple(LatLng(32.0853, 34.7818), "Cafe Cafe Dizengoff", "לב העיר.")
      )
      "greg" -> listOf(
        Triple(LatLng(32.7940, 34.9896), "Greg Haifa", "קפה עם נוף לים."),
        Triple(LatLng(31.9635, 34.7896), "Greg Rishon LeZion", "קניון הזהב."),
        Triple(LatLng(31.8980, 34.8106), "Greg Rehovot", "רחוב הרצל.")
      )
      "landwer" -> listOf(
        Triple(LatLng(32.0684, 34.8248), "Landwer Ramat Gan", "רמת גן - פארק הירקון."),
        Triple(LatLng(32.1093, 34.8372), "Landwer Ramat Aviv", "מרכז שוסטר."),
        Triple(LatLng(31.9730, 34.7730), "Landwer Rishon LeZion", "סינמה סיטי.")
      )
      else -> emptyList()
    }

    if (results.isEmpty()) {
      Toast.makeText(requireContext(), "No data available for \"$query\"", Toast.LENGTH_SHORT).show()
      return
    }

    results.forEach { (location, title, description) ->
      addCoffeeMarker(location, title, description)
    }

    mMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(results[0].first, 12f))
  }

  private fun openCoffeeShopDetails(marker: Marker) {
    val title = marker.title ?: "Coffee Shop"
    val description = marker.tag as? String ?: "No additional info available."

    AlertDialog.Builder(requireContext())
      .setTitle(title)
      .setMessage(description)
      .setPositiveButton("Close", null)
      .show()
  }
}
