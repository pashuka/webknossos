require.config 
  
  baseUrl : "/assets/javascripts"

  paths :
    "jquery" : "libs/jquery-1.7.1"
    "underscore" : "libs/underscore-1.2.0.min"
    "bootstrap" : "libs/bootstrap.min"
    "worker" : "libs/worker_plugin"
    "three": "libs/threejs/three"
    "stats" : "libs/threejs/stats"
    "v3" : "libs/v3"
    "m4x4" : "libs/m4x4"
    "dat" : "libs/dat.gui.min"

  shim : 
    "underscore" :
      exports : "_"
    "bootstrap" : [ "jquery" ]
    "libs/viz" :
      exports : "Viz"
    "routes" :
      exports : "jsRoutes"
    "libs/ace/ace" :
      exports : "ace"
    "three" : 
      exports : "THREE"
    "stats" : 
      exports : "Stats"
    "v3" : 
      exports : "V3"
    "m4x4" : 
      exports : "M4x4"
      

require [
  "jquery"
  "underscore"
  "bootstrap"
  "./main/enhancements"
  "./main/routing"
], ->
     
  
