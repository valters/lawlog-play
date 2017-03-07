var law = new Vue({
  el: '#lawdiff-ajax-content',

  data: {
    fromAjax: false,
    bodyContent: "Loading...",
    currVer: null
  },

  methods: {
    loadLaw: function ( law, ver ) {
        this.toggleVer()
        this.currVer = ver
        this.toggleVer()

        this.fromAjax = true

        var url = "/raw/likums/"+law+"/v/"+ver
        var href = "/likums/"+law+"/v/"+ver

        console.log("loadLaw: GET " + url )

        var vue = this
        $.get( url, function(data) {
            vue.bodyContent = data

            window.history.pushState( null, null, href )
        })
    },

    toggleVer: function() {
        if( this.currVer ) {
            $("#"+this.currVer).toggleClass("active");
        }
    }

  }

})
