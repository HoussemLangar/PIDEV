document.addEventListener('DOMContentLoaded', function(){
  const toggles = document.querySelectorAll('.admin-nav-toggle');
  toggles.forEach(t=>t.addEventListener('click', ()=>{
    document.querySelector('.admin-sidebar').classList.toggle('collapsed');
  }));
});
