
/************************************************
 * CSSI display of SWIM data
 ************************************************/

import Map          from 'ol/Map.js';
import View         from 'ol/View.js';
import {fromLonLat} from 'ol/proj.js';
import Overlay      from 'ol/Overlay.js';
import KML          from 'ol/format/KML.js';
import GeoJSON      from 'ol/format/GeoJSON.js';
import OSM          from 'ol/source/OSM.js';
import VectorSource from 'ol/source/Vector.js';
import {Tile as TileLayer, Vector as VectorLayer} from 'ol/layer.js';
import {Icon, Style, Stroke, Fill, Text, Circle as CircleStyle} from 'ol/style.js';

//var host = "//asdi-db.cssiinc.com:8080";
var host = "//localhost:8080";
var src  = "PCT";
//var src  = "KIAD";   // asdex ???
var seconds_delay = 10;

var cssi_hq = [ -77.015333,  38.883580  ];
var kiad    = [ -77.4599444, 38.9474444 ];

/***************************** popup ***********************/

// soon to be replaced with radar target block
// and later to be populated with src, dest, beacon, gufi, etc.

var pop_container = document.getElementById('popup');
var pop_content   = document.getElementById('popup-content');
var pop_closer    = document.getElementById('popup-closer');

// Create an pop_lyr to anchor the popup to the map.

var pop_lyr = new Overlay( /** @type {olx.OverlayOptions} */ {
    element: pop_container,
    autoPan: true,
    autoPanAnimation: {
        duration: 250
    }
});

/**
 * Add a click handler to hide the popup.
 * @return {boolean} Don't follow the href.
 */
pop_closer.onclick = function() {
    pop_lyr.setPosition(undefined);
    pop_closer.blur();
    return false;
};

/************************ tiles layer ***********************/

var osm_tiles = new TileLayer({
        source: new OSM()
    });

/************************ track styles ***********************/

function targetStyle( feature, resolution) {

    return new Style({
        // TODO: make this an aircraft symbol with heading
        image: new CircleStyle({
            radius: 5,
            fill: new Fill({ color: 'blue' })
        }) ,

        // make this look like a radar target block
        text: new Text({
          //text         : feature.get('acid') + ".." + feature.get('actype') + "\n" + feature.get('alt'),
          text         : feature.get('acid') + "\n" + feature.get('alt'),
          font         : 'bold 11px "Open Sans", "Arial Unicode MS", "sans-serif", "Verdana"',
          fill         : new Fill({ color: 'black' }),
          textAlign    : 'left',
          textBaseline : 'bottom',
          offsetX      :   0,
          offsetY      : -10,
          stroke       : new Stroke({color: '#4dff4d', width: 4}),
          // Q: does text use '\n' or </br> ???
          //text: feature.get('acid') + ... ,
        })

    });
};

// this needs to work for both tracks and targets/points
var trackStyle = function( feature, resolution) {

        switch (feature.get('ctype')) {
            case 'track': return new Style({
                    stroke: new Stroke({ color: 'green', width: 4 }),
                });

            case 'last_pos': return targetStyle( feature, resolution);

            default: return new Style({
                fill: new Fill({ color: 'black' })
            });
        }
};

/************************ track layer ***********************/

var cssihq_geojsonObject = {
    'type': 'FeatureCollection',
//    'crs': {
//        'type': 'name',
//        'properties': {
//            'name': 'EPSG:3857'   // should this be 4326 ???
//        }
//    },
    'features': [{
        'type': 'Feature',
        'geometry': {
            'type': 'Point',
            'coordinates': [ -77.015333,  38.883580 ]
        }
    } ] };

var trackSource = new VectorSource({
        features: (new GeoJSON(
            { dataProjection:    'EPSG:4326',
              featureProjection: 'EPSG:3857'    }
        )).readFeatures( cssihq_geojsonObject )
});

var trackLayer = new VectorLayer({
        source: trackSource,
        style : trackStyle
    });

/************************ the map ***********************/

var map = new Map({
    target  : 'mapid',
    layers  : [ osm_tiles, trackLayer ],
    overlays: [ pop_lyr ],
    view: new View({
        projection: 'EPSG:3857',      // this is the default spherical mercator)
        center    : fromLonLat( kiad ),
        zoom      : 8
    })
});

/************************ ajax ***********************/

function get_cgi_file( ) {

    //listen_on_off_btn.state( 'ajax_sent' );

    var r = getRandomInt(1, 9999);

    console.log("calling ajax");
    var o_url =
        host + "/get_tracks_ajax?apt=" + src + "&random=" + r;

    console.log(o_url);

    var o_url =
    $.ajax({         //method: "GET",
      url: o_url,

      // without this, is it a 'get'? data: { lat: lt, lng: lg, hdg: h, sp: s },

      // following are unknown items from
//https://stackoverflow.com/questions/8567114/how-to-make-an-ajax-call-without-jquery

      context: document.body,

      error: function() {
          // turn off spinner & reset button state
          console.log("error response");
      },

      success: function(){
          console.log('success');
          $(this).addClass("done");   // ???
          // done (below): stop spinning locator (set icon back to dark)
      }
    })
      .done(function( msg ) {
        console.log('done');

        //listen_on_off_btn.state( 'not_listening' );

        update_vector_layer( msg );

      });
};

// ================================

function getRandomInt(min, max) {
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

// ================================

// When you are removing and adding features a lot, with a relatively small
// total of features, I'd suggest constructing the source with
// spatialIndex: false.   -- ahocevar
// BUT, I'm not doing feature-by-feature, so that is not needed...

// ================================

function update_vector_layer( geo_msg ) {

    console.log("update_vector_layer");
    console.log( geo_msg );

    console.log("clearing");
    trackSource.clear();

    console.log("adding");

    trackSource.addFeatures( (new GeoJSON(
                { dataProjection:    'EPSG:4326',
                  featureProjection: 'EPSG:3857'   }
            )).readFeatures( geo_msg ) );

    console.log("done");

    console.log("changed");
    trackLayer.changed();   // Q: is this necessary???

    console.log("render");
    map.render();    // redraw???
};

/************************ delay ***********************/

// get_cgi_file( );  // start the first one now


setInterval(function() {

    get_cgi_file( );

}, seconds_delay * 1000);


/************************ ol5 popup **********************/

// Add a click handler to the map to render the popup.

map.on('singleclick', function(evt) {

    console.log("singleclick");

    var coordinate = evt.coordinate;

    var feature = map.forEachFeatureAtPixel( evt.pixel,
            function(feature, layer) {
                return feature;
    });

    if (feature) {

        // PROBLEM: if no symbol, will still show "undetermined"

        var trknum   = feature.get('trknum');
        var acid     = feature.get('acid');
        var acontent = "<b>" + acid + "</b><br/>trk:" + trknum;
        pop_content.innerHTML = acontent;

        pop_lyr.setPosition(coordinate);
    }
    console.log("done");
});

