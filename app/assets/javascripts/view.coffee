### define
libs/flycam : Flycam
libs/flycam2 : Flycam2d
model : Model
###
    
# global View variables
cam2d = null

# constants
# display 384px out of 512px total width and height
CAM_DISTANCE = 384/2  #alt: 96
PLANE_XY = 0
PLANE_YZ = 1
PLANE_XZ = 2
VIEW_3D  = 3

View =
  initialize : ->

    # The "render" div serves as a container for the canvas, that is 
    # attached to it once a renderer has been initalized.
    container = $("#render")
    # Create a 4x4 grid
    WIDTH = (container.width()-48)/2
    HEIGHT = (container.height()-48)/2

    # Initialize main THREE.js components
    @rendererxy = new THREE.WebGLRenderer({ clearColor: 0xffffff, antialias: true })
    @cameraxy = new THREE.PerspectiveCamera(90, WIDTH / HEIGHT, 1, 1000)
    @scenexy = new THREE.Scene()

    @rendereryz = new THREE.WebGLRenderer({ clearColor: 0x0000ff, antialias: true })
    @camerayz = new THREE.PerspectiveCamera(90, WIDTH / HEIGHT, 0.1, 10000)
    @sceneyz = new THREE.Scene()

    @rendererxz = new THREE.WebGLRenderer({ clearColor: 0x0000ff, antialias: true })
    @cameraxz = new THREE.PerspectiveCamera(90, WIDTH / HEIGHT, 0.1, 10000)
    @scenexz = new THREE.Scene()

    @rendererPrev = new THREE.WebGLRenderer({ clearColor: 0xffffff, antialias: true })
    @cameraPrev = new THREE.OrthographicCamera(-2100, 2100, 2100, -2100, -100000, 100000)
    @scenePrev = new THREE.Scene()

    # Let's set up cameras
    # The cameras are never "moved". They only look at the scenes
    # (the trianglesplanes in particular)
    @scenexy.add(@cameraxy)
    @cameraxy.position.z = CAM_DISTANCE
    @cameraxy.lookAt(new THREE.Vector3( 0, 0, 0 ))

    @sceneyz.add(@camerayz)
    @camerayz.position.z = CAM_DISTANCE
    @camerayz.lookAt(new THREE.Vector3( 0, 0, 0 ))

    @scenexz.add(@cameraxz)
    @cameraxz.position.z = CAM_DISTANCE
    @cameraxz.lookAt(new THREE.Vector3( 0, 0, 0 ))

    @scenePrev.add(@cameraPrev)
    @changePrev VIEW_3D

    # Attach the canvas to the container
    # DEBATE: a canvas can be passed the the renderer as an argument...!?
    @rendererxy.setSize(WIDTH, HEIGHT)
    @rendereryz.setSize(WIDTH, HEIGHT)
    @rendererxz.setSize(WIDTH, HEIGHT)
    @rendererPrev.setSize(WIDTH, HEIGHT)
    container.append(@rendererxy.domElement)
    container.append(@rendereryz.domElement)
    container.append(@rendererxz.domElement)
    container.append(@rendererPrev.domElement)

    @prevControls = $('#prevControls')
    @button3D = document.createElement("input")
    @button3D.setAttribute "type", "button"
    @button3D.setAttribute "value", "3D View"
    @button3D.addEventListener "click", @changePrev3D, true
    @prevControls.append @button3D
    @buttonXY = document.createElement("input")
    @buttonXY.setAttribute "type", "button"
    @buttonXY.setAttribute "value", "XY Plane"
    @buttonXY.addEventListener "click", @changePrevXY, true
    @prevControls.append @buttonXY
    @buttonYZ = document.createElement("input")
    @buttonYZ.setAttribute "type", "button"
    @buttonYZ.setAttribute "value", "YZ Plane"
    @buttonYZ.addEventListener "click", @changePrevYZ, true
    @prevControls.append @buttonYZ
    @buttonXZ = document.createElement("input")
    @buttonXZ.setAttribute "type", "button"
    @buttonXZ.setAttribute "value", "XZ Plane"
    @buttonXZ.addEventListener "click", @changePrevXZ, true
    @prevControls.append @buttonXZ


    # This "camera" is not a camera in the traditional sense.
    # It just takes care of the global position
    cam2d = new Flycam2d CAM_DISTANCE

    # FPS stats
    stats = new Stats()
    stats.getDomElement().style.position = 'absolute'
    stats.getDomElement().style.left = '0px'
    stats.getDomElement().style.top = '0px'
    $("body").append stats.getDomElement() 
    @stats = stats
    @positionStats = $("#status")

    # start the rendering loop
    @animate()

    # Dont forget to handle window resizing!
    $(window).resize( => @.resize() )

    # refresh the scene once a bucket is loaded
    # FIXME: probably not the most elgant thing to do
    $(window).on("bucketloaded", => cam2d.hasChanged = true; cam2d.newBuckets = true) 

  animate : ->
    @renderFunction()

    window.requestAnimationFrame => @animate()

  # This is the main render function.
  # All 3D meshes and the trianglesplane are rendered here.
  renderFunction : ->

    # skip rendering if nothing has changed
    # This prevents you the GPU/CPU from constantly
    # working and keeps your lap cool
    # ATTENTION: this limits the FPS to 30 FPS (depending on the keypress update frequence)
    if cam2d.hasChanged is false
      return

    @updateTrianglesplane()

    # update postion and FPS displays
    position2d = cam2d.getGlobalPos()
    texturePositionXY = cam2d.texturePositionXY
    # without rounding the position becomes really long and blocks the canvas mouse input
    position2d = [Math.round(position2d[0]),Math.round(position2d[1]),Math.round(position2d[2])]
    texturePositionXY = [Math.round(texturePositionXY[0]),Math.round(texturePositionXY[1]),Math.round(texturePositionXY[2])]
    @positionStats.html "Flycam2d: #{position2d}<br />texturePositionXY: #{texturePositionXY}<br />ZoomStep #{cam2d.getZoomStep(0)}<br />activePlane: #{cam2d.getActivePlane()}" 
    @stats.update()

    cam2d.hasChanged = false
    @rendererxy.render @scenexy, @cameraxy
    @rendereryz.render @sceneyz, @camerayz
    @rendererxz.render @scenexz, @cameraxz
    @rendererPrev.render @scenePrev, @cameraPrev

  # Let's apply new pixels to the trianglesplane.
  # We do so by apply a new texture to it.
  updateTrianglesplane : ->
      # new trianglesplane for xy
      return unless @trianglesplanexy
      gxy = @trianglesplanexy

      # new trianglesplane for yz
      return unless @trianglesplaneyz
      gyz = @trianglesplaneyz

      # new trianglesplane for xz
      return unless @trianglesplanexz
      gxz = @trianglesplanexz

      # new trianglesplanes preview
      return unless @trianglesplanePrevXY
      gpxy = @trianglesplanePrevXY
      return unless @trianglesplanePrevYZ
      gpyz = @trianglesplanePrevYZ
      return unless @trianglesplanePrevXZ
      gpxz = @trianglesplanePrevXZ

      # sends current position to Model for preloading data
      # NEW with direction vector
      # Model.Binary.ping cam2d.getGlobalPos(), cam2d.getDirection(), cam2d.getZoomStep(PLANE_XY)
      Model.Binary.ping cam2d.getGlobalPos(), cam2d.getZoomStep(PLANE_XY)

      # sends current position to Model for caching route
      Model.Route.put cam2d.getGlobalPos()

      # trilinear interpolation
      # we used to do this on the shader (trianglesplane.vs)
      # but we switched to this software method in order to use texture on the 
      # trianglesplane
      # This is likely to change in the future, if we manage to do the selection,
      # of the corresponding vertices on the GPU
      if cam2d.needsUpdateXY()
        Model.Binary.getXY(cam2d.getGlobalPos(), cam2d.getZoomStep(PLANE_XY)).done (bufferxy) ->
          
          # ATTENTION 
          # when playing around with texture please look at setTexture() (line 5752 in WebGLRenderer)
          # the data attribute is only available for DataTexture (in other cases it is only texture.image)
          gxy.texture.image.data.set(bufferxy)

          # Update the texture data and make sure the new texture
          # is used by the Mesh's material.
          gxy.texture.needsUpdate = true
          gxy.material.map = gxy.texture

          # TODO implement other two interfaces to get new textures, for now just use the old buffer
          #Model.Binary.getyz(cam2d.getGlobalPos(), cam2d.getZoomStep(PLANE_YZ)).done (bufferyz) ->
          gyz.texture.image.data.set(bufferxy)
          gyz.texture.needsUpdate = true
          gyz.material.map = gyz.texture
  
        #Model.Binary.getyz(cam2d.getGlobalPos(), cam2d.getZoomStep(PLANE_XZ)).done (bufferxz) ->
          gxz.texture.image.data.set(bufferxy)
          gxz.texture.needsUpdate = true
          gxz.material.map = gxz.texture

          gxy.position = new THREE.Vector3(0, 0, 0)
          cam2d.notifyNewTextureXY()
      else
        gxy.position = new THREE.Vector3(-cam2d.getGlobalPos()[0]+cam2d.getTexturePositionXY()[0],
                                          cam2d.getGlobalPos()[1]-cam2d.getTexturePositionXY()[1], 0)
        # The following two lines might actually accomplish that faster, but for some reason this
        # moves the plan along the z axis...
        #
        #gxy.translateX(-(cam2d.getGlobalPos()[0]-cam2d.getTexturePositionXY()[0])-gxy.position.x)
        #gxy.translateY((cam2d.getGlobalPos()[1]-cam2d.getTexturePositionXY()[1])-gxy.position.y)

      globalPos = cam2d.getGlobalPos()
      # Translating ThreeJS' coordinate system to the preview's one
      globalPosVec = new THREE.Vector3(globalPos[0], 2500-globalPos[2], globalPos[1])

      # Cropping and mapping the Textures to preview planes
      offsetsXY = cam2d.getOffsetsXY()
      gpxy.position = globalPosVec
      gpxy.texture = gxy.texture.clone()
      gpxy.texture.needsUpdate = true
      gpxy.material.map = gpxy.texture
      gpxy.material.map.repeat.x = CAM_DISTANCE*2 / 512;
      gpxy.material.map.repeat.y = CAM_DISTANCE*2 / 512;
      gpxy.material.map.offset.x = offsetsXY[0] / 512;
      gpxy.material.map.offset.y = offsetsXY[1] / 512;
      
      gpyz.position = globalPosVec
      gpyz.texture = gyz.texture.clone()
      gpyz.texture.needsUpdate = true
      gpyz.material.map = gpyz.texture
      gpyz.material.map.repeat.x = CAM_DISTANCE*2 / 512;
      gpyz.material.map.repeat.y = CAM_DISTANCE*2 / 512;
      gpyz.material.map.offset.x = (512 - CAM_DISTANCE*2) / 2 / 512;
      gpyz.material.map.offset.y = (512 - CAM_DISTANCE*2) / 2 / 512;
      
      gpxz.position = globalPosVec
      gpxz.texture = gxz.texture.clone()
      gpxz.texture.needsUpdate = true
      gpxz.material.map = gpxz.texture
      gpxz.material.map.repeat.x = CAM_DISTANCE*2 / 512;
      gpxz.material.map.repeat.y = CAM_DISTANCE*2 / 512;
      gpxz.material.map.offset.x = (512 - CAM_DISTANCE*2) / 2 / 512;
      gpxz.material.map.offset.y = (512 - CAM_DISTANCE*2) / 2 / 512;
  
  # Adds a new Three.js geometry to the scene.
  # This provides the public interface to the GeometryFactory.
  addGeometryXY : (geometry) ->
    @scenexy.add geometry

  addGeometryYZ : (geometry) ->
    @sceneyz.add geometry

  addGeometryXZ : (geometry) ->
    @scenexz.add geometry

  addGeometryPrev : (geometry) ->
    @scenePrev.add geometry

  changePrev : (id) ->
    @cameraPrev.position.x = 1250   #so it looks at the 2500x2500 cube
    @cameraPrev.position.y = 1250
    @cameraPrev.position.z = 1250
    switch id
      when VIEW_3D
        @cameraPrev.position.x = 4000   #so it looks at the 2500x2500 cube
        @cameraPrev.position.y = 4000
        @cameraPrev.position.z = 5000
      when PLANE_XY
        @cameraPrev.position.y = 4000
      when PLANE_YZ
        @cameraPrev.position.x = 4000
      when PLANE_XZ
        @cameraPrev.position.z = 4000
    @cameraPrev.lookAt(new THREE.Vector3( 1250, 1250, 1250 ))
    if cam2d then cam2d.hasChanged = true

  changePrev3D : => View.changePrev(VIEW_3D)
  changePrevXY : => View.changePrev(PLANE_XY)
  changePrevYZ : => View.changePrev(PLANE_YZ)
  changePrevXZ : => View.changePrev(PLANE_XZ)

  #Apply a single draw (not used right now)
  draw : ->
    #FIXME: this is dirty
    cam2d.hasChanged = true

  #Call this after the canvas was resized to fix the viewport
  resize : ->
    #FIXME: Is really the window's width or rather the DIV's?
    container = $("#render")
    WIDTH = (container.width()-49)/2
    HEIGHT = (container.height()-49)/2

    @rendererxy.setSize( WIDTH, HEIGHT )
    @rendereryz.setSize( WIDTH, HEIGHT )
    @rendererxz.setSize( WIDTH, HEIGHT )
    @rendererPrev.setSize( WIDTH, HEIGHT)
    @cameraxy.aspect  = WIDTH / HEIGHT
    @cameraxy.updateProjectionMatrix()
    @camerayz.aspect  = WIDTH / HEIGHT
    @camerayz.updateProjectionMatrix()
    @cameraxz.aspect  = WIDTH / HEIGHT
    @cameraxz.updateProjectionMatrix()
    @cameraPrev.aspect = WIDTH / HEIGHT
    @cameraPrev.updateProjectionMatrix()
    @draw()

############################################################################
#Interface for Controller
  # TODO: Some of those are probably obsolete

  setGlobalPos : (pos) ->
    cam2d.setGlobalPos(pos)

  getGlobalPos : ->
    cam2d.getGlobalPos()

  setDirection : (direction) ->
    cam2d.setDirection direction

  move : (p) ->
    cam2d.move p

  moveActivePlane : (p) ->
    switch (cam2d.getActivePlane())
      when PLANE_XY
        cam2d.move p
      when PLANE_YZ
        cam2d.move [p[2], p[1], p[0]]
      when PLANE_XZ
        cam2d.move [p[0], p[2], p[1]]

  #FIXME: why can't I call move() from within this function?
  moveX : (x) -> 
    cam2d.move [x, 0, 0]

  moveY : (y) ->
    cam2d.move [0, y, 0]
  
  moveZ : (z) ->
    cam2d.move [0, 0, z]

  scaleTrianglesPlane : (delta) ->
    @x = 1 unless @x
    if (@x+delta > 0.5) and (@x+delta < 1.5)
      @x += Number(delta)
      WIDTH = HEIGHT = @x * 384
      container = $("#render")
      container.width(2 * WIDTH + 48)
      container.height(2 * HEIGHT + 48)
      @resize()

  zoomIn : ->
    if cam2d.getZoomStep(cam2d.getActivePlane()) > 0
      cam2d.zoomIn(cam2d.getActivePlane())

  #todo: validation in Model
  zoomOut : ->
    if cam2d.getZoomStep(cam2d.getActivePlane()) < 3
      cam2d.zoomOut(cam2d.getActivePlane())

  setActivePlane : (activePlane) ->
    cam2d.setActivePlane activePlane
    cam2d.hasChanged = true

  setActivePlaneXY : ->
    cam2d.setActivePlane PLANE_XY
    $("canvas")[0].style.borderColor = "#FFDD00"
    $("canvas")[1].style.borderColor = "#C7D1D8"
    $("canvas")[2].style.borderColor = "#C7D1D8"
    cam2d.hasChanged = true

  setActivePlaneYZ : ->
    cam2d.setActivePlane PLANE_YZ
    $("canvas")[0].style.borderColor = "#C7D1D8"
    $("canvas")[1].style.borderColor = "#FFDD00"
    $("canvas")[2].style.borderColor = "#C7D1D8"
    cam2d.hasChanged = true

  setActivePlaneXZ : ->
    cam2d.setActivePlane PLANE_XZ
    $("canvas")[0].style.borderColor = "#C7D1D8"
    $("canvas")[1].style.borderColor = "#C7D1D8"
    $("canvas")[2].style.borderColor = "#FFDD00"
    cam2d.hasChanged = true
