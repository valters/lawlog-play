var law = new Vue({
  el: '#lawdiff-ajax-content',

  data: {
    bodyContent: null,
    currVer: null
  },

  methods: {
    // seed with current version
    seedLaw: function( ver ) {
        this.currVer = ver
        console.log("seedLaw: " + this.currVer )
    },

    loadLaw: function ( law, ver ) {
        console.log("loadLaw: " + this.currVer + " -> " + ver )
        if( this.currVer == ver ) {
            return // do nothing
        }

        this.toggleVer()
        this.currVer = ver
        this.toggleVer()

        var url = '/raw/likums/'+law+'/v/'+ver
        var href = '/likums/'+law+'/v/'+ver

        console.log("loadLaw: GET " + url )

        var vue = this
        $.get( url, function(data) {
            vue.hideServerSideContent()

            vue.bodyContent = data

            window.history.pushState( null, null, href )
        })
    },

    // mark (or unmark) active law version css
    toggleVer: function() {
        if( this.currVer ) {
            $('#'+ this.currVer ).toggleClass('active');
        }
    },

    // before we do ajax replace, we want to hide something that was pre-rendered server side
    hideServerSideContent: function() {
        $('#lawdiff-server-side').hide()
    }

  }

})
