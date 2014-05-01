### define
underscore : _
backbone.marionette : marionette
./skeleton_tracing_settings_view : SkeletonTracingSettingsView
./volume_tracing_settings_view : VolumeTracingSettingsView
./plane_user_settings_view : PlaneUserSettingsView
./arbitrary_user_settings_view : ArbitraryUserSettingsView
./dataset_settings_view : DatasetSettingsView
oxalis/constants : constants
###

class SettingsTabView extends Backbone.Marionette.Layout

  className : "col-sm-12"
  id : "lefttabbar"

  template : _.template("""
    <ul class="nav nav-tabs">
      <li class="active">
        <a href="#tracing-settings-tab" data-toggle="tab"><i class="fa fa-cogs"></i> Tracing</a>
      </li>
      <li>
        <a href="#dataset-settings-tab" data-toggle="tab"><i class="fa fa-cogs"></i> Dataset</a>
      </li>
      <li>
        <a href="#user-settings-tab" data-toggle="tab"><i class="fa fa-cogs"></i> User</a>
      </li>
    </ul>

    <div class="tab-content">
      <div class="tab-pane active" id="tracing-settings-tab"></div>
      <div class="tab-pane" id="dataset-settings-tab"></div>
      <div class="tab-pane" id="user-settings-tab"></div>
    </div>
  </div>
  """)

  regions :
    "tracingSettings" : "#tracing-settings-tab"
    "userSettings" : "#user-settings-tab"
    "datasetSettings" : "#dataset-settings-tab"


  initialize : (options) ->

    @options = options

    @skeletonTracingSettingsView = new SkeletonTracingSettingsView(_model : options._model)
    @volumeTracingSettingsView = new VolumeTracingSettingsView(_model : options._model)

    @planeUserSettingsView = new PlaneUserSettingsView(_model : options._model)
    @arbitraryUserSettingsView = new ArbitraryUserSettingsView(_model : options._model)

    @datasetSettingsView = new DatasetSettingsView(_model : options._model)

    @listenTo(@, "render", @afterRender)
    @listenTo(app.vent, "changeViewMode", @changeViewMode)


  afterRender : ->

      @datasetSettings.show(@datasetSettingsView)


  changeViewMode : (mode) ->

    if mode == constants.MODE_PLANE_TRACING
      @userSettings.show(@planeUserSettingsView)
      @tracingSettings.show(@skeletonTracingSettingsView)
    else if mode in constants.MODES_ARBITRARY
      @userSettings.show(@arbitraryUserSettingsView)
      @tracingSettings.show(@skeletonTracingSettingsView)
    else if mode == constants.MODE_VOLUME
      @userSettings.show(@planeUserSettingsView)
      @tracingSettings.show(@volumeTracingSettingsView)
