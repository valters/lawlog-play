if (window.console) {
  console.log("Welcome to your Play application's JavaScript!");
}

$(document).ready(function(){
    $(".push_menu").click(function(){
         $(".wrapper").toggleClass("active");
    });
});
