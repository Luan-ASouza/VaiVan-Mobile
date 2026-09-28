import { auth, db } from "./firebase-config.js";
import { signInWithEmailAndPassword } from "https://www.gstatic.com/firebasejs/10.12.0/firebase-auth.js";
import { doc, getDoc } from "https://www.gstatic.com/firebasejs/10.12.0/firebase-firestore.js";

const form = document.getElementById("formLogin");
const inputEmail = document.getElementById("email");
const inputSenha = document.getElementById("senha");
const divErro = document.getElementById("erro");

form.addEventListener("submit", async (evento) => {
  evento.preventDefault();
  divErro.textContent = "";

  const email = inputEmail.value.trim();
  const senha = inputSenha.value.trim();

  try {
    // Passo 1: tenta logar no Firebase Authentication
    const resultado = await signInWithEmailAndPassword(auth, email, senha);
    const uid = resultado.user.uid;

    // Passo 2: verifica se esse UID está na coleção "admins"
    const refAdmin = doc(db, "admins", uid);
    const snapshotAdmin = await getDoc(refAdmin);

    if (snapshotAdmin.exists()) {
      // É admin de verdade, pode entrar no painel
      window.location.href = "painel.html";
    } else {
      // Logou certo, mas não é admin -- bloqueia o acesso
      divErro.textContent = "Este usuário não tem permissão de administrador.";
      await auth.signOut();
    }
  } catch (erro) {
    divErro.textContent = "Email ou senha incorretos.";
    console.error(erro);
  }
});