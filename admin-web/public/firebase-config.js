// Importa os SDKs do Firebase direto via CDN (sem precisar instalar nada com npm)
import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.0/firebase-app.js";
import { getAuth } from "https://www.gstatic.com/firebasejs/10.12.0/firebase-auth.js";
import { getFirestore } from "https://www.gstatic.com/firebasejs/10.12.0/firebase-firestore.js";

const firebaseConfig = {
  apiKey: "AIzaSyAA4736QybnspHFx7HXHVp2l6gZMIMZt3Y",
  authDomain: "vaivanvaicontigo.firebaseapp.com",
  projectId: "vaivanvaicontigo",
  storageBucket: "vaivanvaicontigo.firebasestorage.app",
  messagingSenderId: "639129984068",
  appId: "1:639129984068:web:0ffa0b024cf2a8d6233011"
};

const app = initializeApp(firebaseConfig);

export const auth = getAuth(app);
export const db = getFirestore(app);