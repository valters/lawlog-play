if (window.console) {
    console.log("Welcome to your Play application's JavaScript!");
}

$(document).ready(function(){
    $(".push_menu").click(function(){
         $(".wrapper").toggleClass("active");
    });
})


new Vue({
  el: '#test-json',

  data: {
    body_content: "Loading..."
  },

  mounted: function() {

    console.log("IN VUE")

    var vue = this
    $.get( "/raw/likums/satversme/v/20140722", function(data) {
        vue.body_content = data
        console.log("IN content")
    })

  },

})
