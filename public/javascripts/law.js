var law = new Vue({
  el: '#lawdiff-ajax-content',

  data: {
    body_content: "Loading..."
  },

  mounted: function() {
    console.log("IN VUE")

    this.loadLaw( "satversme", "20140722" )
  },

  methods: {
    loadLaw: function ( law, ver ) {
        var url = "/raw/likums/"+law+"/v/"+ver

        console.log("loadLaw: GET " + url )

        var vue = this
        $.get( url, function(data) {
            vue.body_content = data
        })
    }
  }

})
