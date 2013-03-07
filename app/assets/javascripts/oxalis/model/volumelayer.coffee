### define 
./dimensions : Dimensions
###

PLANE_XY         = Dimensions.PLANE_XY
PLANE_YZ         = Dimensions.PLANE_YZ
PLANE_XZ         = Dimensions.PLANE_XZ

MODE_NORMAL      = 0
MODE_SUB         = 1
MODE_ADD         = 2

class VolumeLayer
  
  constructor : (@cell, @plane, @thirdDimensionValue, @id, @time) ->
    
    unless @time?
      @time = (new Date()).getTime()
    @contourList = []
    @helperList  = []         # used to implement add/substract
    @comment     = ""
    @maxCoord    = null
    @minCoord    = null
    @mode        = MODE_NORMAL

  setMode : (newMode) ->
    @mode = newMode
    @helperList = []

  addContour : (pos) ->
    if @mode == MODE_NORMAL
      @contourList.push(pos)
    else
      @helperList.push(pos)

    unless @maxCoord?
      @maxCoord = pos.slice()
      @minCoord = pos.slice()

    for i in [0..2]
      @minCoord[i] = Math.min(@minCoord[i], pos[i])
      @maxCoord[i] = Math.max(@maxCoord[i], pos[i])

  # Finalize add / substract
  finishLayer : ->

    if @mode == MODE_NORMAL then return

    # Intersect contourList with helperList
    cIn = []; cOut = []; isIn = null
    
    for pos in @contourList
      newIsIn = @containsVoxel(pos, @helperList)
      list = if newIsIn then cIn else cOut

      if isIn != newIsIn
        list.push([])
      list[ list.length - 1 ].push(pos)
      isIn = newIsIn

    # Intersect helperList with contourList
    hIn = []; hOut = []; isIn = null
    
    for pos in @helperList
      newIsIn = @containsVoxel(pos)
      list = if newIsIn then hIn else hOut

      if isIn != newIsIn
        list.push([])
      list[ list.length - 1 ].push(pos)
      isIn = newIsIn

    # newContourList contains cOut and either
    # hOut (MODE_ADD) or hIn (MODE_SUB)
    if @mode == MODE_ADD then partsList = cOut.concat(hOut)
    if @mode == MODE_SUB then partsList = cOut.concat(hIn)

    # Construct newContourList by putting the parts together
    newContourList = partsList.splice(0,1)[0]
    while partsList.length > 0
      pos = newContourList[newContourList.length - 1]
      closestPart = {
        distance  : Dimensions.distance(partsList[0][0], pos)
        partIndex : 0,
        reversed  : false 
      }

      for i in [0...partsList.length]
        list = partsList[i]

        for reversed in [true, false]
          index = if reversed then list.length - 1 else 0
          distance = Dimensions.distance(list[index], pos)

          if distance < closestPart.distance
            closestPart.distance  = distance
            closestPart.partIndex = i
            closestPart.reversed  = reversed

      partToAdd = partsList.splice(closestPart.partIndex, 1)[0]
      if closestPart.reversed
        partToAdd.reverse()
      newContourList = newContourList.concat(partToAdd)

    @contourList = newContourList.concat([newContourList[0]])
    @mode = MODE_NORMAL

  containsVoxel : (voxelCoordinate, list = @contourList) ->
    
    thirdDimension = Dimensions.thirdDimensionForPlane(@plane)
    if voxelCoordinate[thirdDimension] != @thirdDimensionValue
      return false

     # Algorithm described in OX-322
    totalDiff = 0
    point = @get2DCoordinate(voxelCoordinate)

    for contour in list

      contour2d = @get2DCoordinate(contour)
      newQuadrant = @getQuadrantWithRespectToPoint(contour2d, point)
      prevQuadrant = if quadrant? then quadrant else newQuadrant
      quadrant = newQuadrant
      
      if Math.abs(prevQuadrant - quadrant) == 2 or quadrant == 0
        # point is on the edge, considered within the polygon
        #console.log "Point is ON the edge", prevQuadrant, quadrant
        return true
      diff = quadrant - prevQuadrant
      # special cases if quadrants are 4 and 1
      if diff ==  3 then diff = -1
      if diff == -3 then diff =  1
      totalDiff -= diff

      #if prevQuadrant != quadrant
      #  console.log prevQuadrant, "-->", quadrant
      

    #console.log "totalDiff", totalDiff
    return totalDiff != 0

  getVoxelArray : ->

    res = []
    # Check every voxel in this cuboid
    startTime = new Date().getTime()
    for x in [@minCoord[0]..@maxCoord[0]]
      for y in [@minCoord[1]..@maxCoord[1]]
        for z in [@minCoord[2]..@maxCoord[2]]
          if @containsVoxel([x, y, z])
            res.push([x, y, z])
    console.log "Time", (new Date().getTime() - startTime)
    console.log "Cuboid", @minCoord, @maxCoord

    return res

  get2DCoordinate : (coord3d) ->
    # Throw out 'thirdCoordinate' which is equal anyways

    result = []
    for i in [0..2]
      if i != Dimensions.thirdDimensionForPlane(@plane)
        result.push(coord3d[i])
    return result

  getQuadrantWithRespectToPoint : (vertex, point) ->
    xDiff = vertex[0] - point[0]
    yDiff = vertex[1] - point[1]

    if xDiff == 0 and yDiff == 0
      # Vertex and point have the same coordinates
      return 0
    
    switch
      when xDiff <= 0 and yDiff >  0 then return 1
      when xDiff <= 0 and yDiff <= 0 then return 2
      when xDiff >  0 and yDiff <= 0 then return 3
      when xDiff >  0 and yDiff >  0 then return 4