import { auth, db } from "./firebase-config.js";
import { onAuthStateChanged, signOut } from "https://www.gstatic.com/firebasejs/10.12.0/firebase-auth.js";
import {
  collection,
  query,
  where,
  onSnapshot,
  doc,
  getDoc,
  updateDoc
} from "https://www.gstatic.com/firebasejs/10.12.0/firebase-firestore.js";

const listaDocumentos = document.getElementById("listaDocumentos");
const btnSair = document.getElementById("btnSair");

// Verifica se o usuário está logado E é admin, toda vez que a página carrega
onAuthStateChanged(auth, async (usuario) => {
  if (!usuario) {
    // Não está logado -> manda de volta pro login
    window.location.href = "index.html";
    return;
  }

  const refAdmin = doc(db, "admins", usuario.uid);
  const snapshotAdmin = await getDoc(refAdmin);

  if (!snapshotAdmin.exists()) {
    // Está logado, mas não é admin -> bloqueia
    alert("Você não tem permissão de administrador.");
    await signOut(auth);
    window.location.href = "index.html";
    return;
  }

  // É admin de verdade -> pode carregar os documentos
  carregarDocumentosPendentes();
});

// Botão de sair
btnSair.addEventListener("click", async () => {
  await signOut(auth);
  window.location.href = "index.html";
});

// Busca em tempo real os documentos com status "EM_ANALISE"
function carregarDocumentosPendentes() {
  const referenciaColecao = collection(db, "documentos");
  const consulta = query(referenciaColecao, where("statusValidacao", "==", "EM_ANALISE"));

  onSnapshot(consulta, (snapshot) => {
    listaDocumentos.innerHTML = "";

    if (snapshot.empty) {
      listaDocumentos.innerHTML = "<p class='vazio'>Nenhum documento pendente no momento.</p>";
      return;
    }

    snapshot.forEach((docSnapshot) => {
      const dados = docSnapshot.data();
      const id = docSnapshot.id;

      const card = criarCardDocumento(id, dados);
      listaDocumentos.appendChild(card);
    });
  });
}

// Monta o "cartão" visual de cada documento
function criarCardDocumento(id, dados) {
  const card = document.createElement("div");
  card.className = "card-documento";

  card.innerHTML = `
    <img src="${dados.arquivo}" alt="Documento" class="img-documento" />
    <div class="info-documento">
      <p><strong>Tipo:</strong> ${dados.tipo ?? "Não informado"}</p>
      <p><strong>Motorista ID:</strong> ${dados.motoristaId ?? "-"}</p>
      <p><strong>Data de envio:</strong> ${dados.dataEnvio ?? "-"}</p>
    </div>
    <div class="acoes-documento">
      <button class="btn-aprovar">Aprovar</button>
      <button class="btn-rejeitar">Rejeitar</button>
    </div>
  `;

  card.querySelector(".btn-aprovar").addEventListener("click", () => {
    aprovarDocumento(id);
  });

  card.querySelector(".btn-rejeitar").addEventListener("click", () => {
    const motivo = prompt("Motivo da rejeição:");
    if (motivo && motivo.trim() !== "") {
      rejeitarDocumento(id, motivo.trim());
    }
  });

  return card;
}

async function aprovarDocumento(id) {
  const refDocumento = doc(db, "documentos", id);
  await updateDoc(refDocumento, {
    statusValidacao: "APROVADO"
  });
}

async function rejeitarDocumento(id, motivo) {
  const refDocumento = doc(db, "documentos", id);
  await updateDoc(refDocumento, {
    statusValidacao: "REJEITADO",
    motivoRejeicao: motivo
  });
}