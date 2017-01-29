if (window.console) {
    console.log("Welcome to your Play application's JavaScript!");
}

$(document).ready(function(){
    $(".push_menu").click(function(){
         $(".wrapper").toggleClass("active");
    });

    $.get( "/raw/likums/satversme/v/20140722", function(data) {
        $( "#test-json" ).replaceWith(data);
    });

});
