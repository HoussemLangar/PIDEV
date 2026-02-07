document.addEventListener('DOMContentLoaded', function(){
  const menu = document.querySelector('.menu-toggle');
  const nav = document.querySelector('.nav-links');
  if(menu && nav){
    menu.addEventListener('click', ()=> nav.classList.toggle('open'))
  }
});
