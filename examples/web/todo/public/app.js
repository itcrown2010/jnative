// Author: 张晓达
var listEl = document.getElementById("list");
var form = document.getElementById("add-form");
var titleInput = document.getElementById("title");

function render(todos) {
  listEl.innerHTML = "";
  todos.forEach(function (todo) {
    var li = document.createElement("li");
    if (todo.done) {
      li.className = "done";
    }
    var cb = document.createElement("input");
    cb.type = "checkbox";
    cb.checked = !!todo.done;
    cb.addEventListener("change", function () {
      fetch("/api/todos/" + todo.id, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ title: todo.title, done: cb.checked })
      }).then(load);
    });
    var span = document.createElement("span");
    span.textContent = todo.title;
    var del = document.createElement("button");
    del.className = "delete";
    del.textContent = "Delete";
    del.addEventListener("click", function () {
      fetch("/api/todos/" + todo.id, { method: "DELETE" }).then(load);
    });
    li.appendChild(cb);
    li.appendChild(span);
    li.appendChild(del);
    listEl.appendChild(li);
  });
}

function load() {
  fetch("/api/todos")
    .then(function (r) { return r.json(); })
    .then(render);
}

form.addEventListener("submit", function (e) {
  e.preventDefault();
  var title = titleInput.value.trim();
  if (!title) {
    return;
  }
  fetch("/api/todos", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ title: title })
  }).then(function () {
    titleInput.value = "";
    load();
  });
});

load();
