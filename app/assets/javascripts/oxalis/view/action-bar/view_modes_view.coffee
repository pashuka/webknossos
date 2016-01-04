marionette = require("backbone.marionette")
app        = require("app")
constants  = require("oxalis/constants")

class ViewModesView extends Backbone.Marionette.ItemView

  template : _.template("""
    <div class="btn-group btn-group">
      <div class="btn-group">
        <button type="button" class="btn btn-default" id="mode-3planes">Orthogonal</button>
      </div>
      <div class="btn-group">
        <button type="button" class="btn btn-default" id="mode-sphere">Flight</button>
      </div>
      <div class="btn-group">
        <button type="button" class="btn btn-default" id="mode-arbitraryplane">Oblique</button>
      </div>
    </div>
  """)

  modeMapping :
    "mode-3planes" : constants.MODE_PLANE_TRACING
    "mode-sphere" : constants.MODE_ARBITRARY
    "mode-arbitraryplane" : constants.MODE_ARBITRARY_PLANE

  events :
    "click button" : "changeMode"


  initialize : (options) ->

    @listenTo(@model, "change:mode", @updateForMode)


  changeMode : (evt) ->

    mode = @modeMapping[evt.target.id]
    @model.setMode(mode)


  updateForMode : (mode) ->

    @$("button").removeClass("btn-primary")

    buttonId = _.invert(@modeMapping)[mode]
    @$("##{buttonId}").addClass("btn-primary")

module.exports = ViewModesView