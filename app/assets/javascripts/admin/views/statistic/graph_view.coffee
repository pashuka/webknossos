### define
underscore : _
app : app
backbone.marionette : marionette
c3 : c3
admin/models/statistic/time_statistic_collection : TimeStatisticCollection
###

class GraphView extends Backbone.Marionette.ItemView

  template : _.template("""
  """)

  initialize : ->


    @collection = new TimeStatisticCollection()
    @collection.fetch().done(=> @update())

    @listenTo(@collection, "reset", @update)

  update : ->

    graph = c3.generate(
      bindto : ".graph"
      data:
        x: "x"
        columns: [
          ["x"].concat @collection.map((item) -> return item.get("date"))
          ["weekly-hours"].concat @collection.map((item) -> return item.get("timestamp"))
        ]
        selection :
          enabled : true
          grouped : true
      axis :
        x :
          type : "timeseries"
      legend :
        show : false
      point :
        onclick : @selectDataPoint
    )

  selectDataPoint : (data) ->

    app.vent.trigger("graphView:updatedSelection", data)