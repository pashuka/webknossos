### define
./volumecell : VolumeCell
./volumelayer : VolumeLayer
libs/event_mixin : EventMixin
../dimensions : Dimensions
libs/drawing : Drawing
./volumetracing_statelogger : VolumeTracingStateLogger
###

class VolumeTracing

  constructor : (tracing, @flycam, @binary, updatePipeline) ->

    _.extend(@, new EventMixin())

    @contentData  = tracing.content.contentData
    @cells        = []
    @activeCell   = null
    @currentLayer = null        # Layer currently edited
    @idCount      = @contentData.nextCell || 1

    @stateLogger  = new VolumeTracingStateLogger(
      @flycam, tracing.version, tracing.id, tracing.typ,
      tracing.restrictions.allowUpdate,
      updatePipeline,
      this, @binary.pushQueue
    )

    @createCell(@contentData.activeCell)

    # For testing
    window.setAlpha = (v) -> Drawing.setAlpha(v)
    window.setSmoothLength = (v) -> Drawing.setSmoothLength(v)


  # Should be called whenever the model is modified
  # Returns whether the modification should be aborted
  # ==> return if @handleModification()
  handleModification : ->

    return true


  createCell : (id) ->

    return if @handleModification()

    unless id?
      id = @idCount++

    @cells.push( newCell = new VolumeCell(id) )
    @setActiveCell( newCell.id )
    @currentLayer = null


  startEditing : (planeId) ->
    # Return, if layer was actually started

    return false if @handleModification()

    if currentLayer? or @flycam.getIntegerZoomStep() > 0
      return false

    pos = Dimensions.roundCoordinate(@flycam.getPosition())
    thirdDimValue = pos[Dimensions.thirdDimensionForPlane(planeId)]
    @currentLayer = new VolumeLayer(planeId, thirdDimValue)
    return true


  addToLayer : (pos) ->

    return if @handleModification()

    unless @currentLayer?
      return

    @currentLayer.addContour(pos)
    @trigger "updateLayer", @getActiveCellId(), @currentLayer.getSmoothedContourList()

  finishLayer : ->

    return if @handleModification()

    unless @currentLayer?
      return

    start = (new Date()).getTime()
    iterator = @currentLayer.getVoxelIterator()
    labelValue = if @activeCell then @activeCell.id else 0
    @binary.cube.labelVoxels(iterator, labelValue)
    console.log "Labeling time:", ((new Date()).getTime() - start)

    @currentLayer = null
    @flycam.update()

    @trigger "volumeAnnotated"


  getActiveCellId : ->

    if @activeCell?
      return @activeCell.id
    else
      return 0


  setActiveCell : (id) ->

    @activeCell = null
    for cell in @cells
      if cell.id == id then @activeCell = cell

    if not @activeCell? and id > 0
      @createCell(id)

    @trigger "newActiveCell", id
