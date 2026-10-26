import { auth, db } from "./firebase-config.js";
import { onAuthStateChanged, signOut } from "https://www.gstatic.com/firebasejs/10.12.0/firebase-auth.js";
import {
  collection,
  query,
  where,
  onSnapshot,
  doc,
  getDoc,
  getDocs,
  setDoc,
  updateDoc
} from "https://www.gstatic.com/firebasejs/10.12.0/firebase-firestore.js";

const listaDocumentos = document.getElementById("listaDocumentos");
const listaAssinaturas = document.getElementById("listaAssinaturas");
const listaPlanos = document.getElementById("listaPlanos");
const btnSair = document.getElementById("btnSair");

// Documentos que o motorista precisa ter APROVADOS para ser liberado
const TIPOS_OBRIGATORIOS = ["CNH_FRENTE", "CNH_VERSO", "COMPROVANTE_RESIDENCIA"];

const DIA_MS = 24 * 60 * 60 * 1000;
const CARENCIA_DIAS = 3;

// Mesmos planos do aplicativo (PlanoCatalogo.padrao)
const PLANOS_PADRAO = [
  {
    id: "basico", nome: "Plano Básico", valorCentavos: 9900, limiteRotas: 3,
    ilimitado: false, beneficios: ["Até 3 rotas ativas", "Suporte básico"], ativo: true, ordem: 1
  },
  {
    id: "premium", nome: "Plano Premium", valorCentavos: 19900, limiteRotas: 0,
    ilimitado: true, beneficios: ["Rotas ilimitadas", "Suporte 24h"], ativo: true, ordem: 2
  }
];

// Escapa texto vindo do banco antes de colocar no HTML
function esc(valor) {
  return String(valor ?? "").replace(/[&<>"']/g, (c) => (
    { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]
  ));
}

// Só aceita endereço http(s) para imagens
function urlSegura(url) {
  return /^https?:\/\//i.test(url ?? "") ? url : "";
}

function dinheiro(centavos) {
  return (Number(centavos || 0) / 100).toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
}

function data(ms) {
  return ms ? new Date(ms).toLocaleDateString("pt-BR") : "-";
}

// ---------------------------------------------------------
// Acesso e abas
// ---------------------------------------------------------

onAuthStateChanged(auth, async (usuario) => {
  if (!usuario) {
    window.location.href = "index.html";
    return;
  }

  const snapshotAdmin = await getDoc(doc(db, "admins", usuario.uid));

  if (!snapshotAdmin.exists()) {
    alert("Você não tem permissão de administrador.");
    await signOut(auth);
    window.location.href = "index.html";
    return;
  }

  carregarDocumentosPendentes();
  carregarAssinaturas();
  carregarPlanos();
});

btnSair.addEventListener("click", async () => {
  await signOut(auth);
  window.location.href = "index.html";
});

document.querySelectorAll(".aba").forEach((botao) => {
  botao.addEventListener("click", () => {
    document.querySelectorAll(".aba").forEach((b) => b.classList.toggle("ativa", b === botao));
    document.getElementById("abaDocumentos").hidden = botao.dataset.aba !== "documentos";
    document.getElementById("abaAssinaturas").hidden = botao.dataset.aba !== "assinaturas";
    document.getElementById("abaPlanos").hidden = botao.dataset.aba !== "planos";
  });
});

// ---------------------------------------------------------
// Documentos
// ---------------------------------------------------------

function carregarDocumentosPendentes() {
  const consulta = query(collection(db, "documentos"), where("statusValidacao", "==", "EM_ANALISE"));

  onSnapshot(consulta, (snapshot) => {
    listaDocumentos.innerHTML = "";

    if (snapshot.empty) {
      listaDocumentos.innerHTML = "<p class='vazio'>Nenhum documento pendente no momento.</p>";
      return;
    }

    snapshot.forEach((docSnapshot) => {
      listaDocumentos.appendChild(criarCardDocumento(docSnapshot.id, docSnapshot.data()));
    });
  });
}

function criarCardDocumento(id, dados) {
  const card = document.createElement("div");
  card.className = "card-documento";

  const arquivo = urlSegura(dados.arquivo);

  card.innerHTML = `
    ${arquivo
      ? `<a href="${esc(arquivo)}" target="_blank" rel="noopener"><img src="${esc(arquivo)}" alt="Documento" class="img-documento" /></a>`
      : ""}
    <div class="info-documento">
      <p><strong>Tipo:</strong> ${esc(dados.tipo ?? "Não informado")}</p>
      <p><strong>Motorista ID:</strong> ${esc(dados.motoristaId ?? "-")}</p>
      <p><strong>Data de envio:</strong> ${esc(dados.dataEnvio ?? "-")}</p>
    </div>
    <div class="acoes-documento">
      <button class="btn-aprovar">Aprovar</button>
      <button class="btn-rejeitar">Rejeitar</button>
    </div>
  `;

  card.querySelector(".btn-aprovar").addEventListener("click", () => aprovarDocumento(id, dados));

  card.querySelector(".btn-rejeitar").addEventListener("click", () => {
    const motivo = prompt("Motivo da rejeição:");
    if (motivo && motivo.trim() !== "") {
      rejeitarDocumento(id, dados, motivo.trim());
    }
  });

  return card;
}

async function aprovarDocumento(id, dados) {
  await updateDoc(doc(db, "documentos", id), { statusValidacao: "APROVADO" });

  // Documento de veículo ou sem motorista: nada a liberar
  if (!dados.motoristaId || !TIPOS_OBRIGATORIOS.includes(dados.tipo)) return;

  // Se os 3 documentos obrigatórios estiverem aprovados, libera o motorista
  const resultado = await getDocs(
    query(collection(db, "documentos"), where("motoristaId", "==", dados.motoristaId))
  );
  const aprovados = new Set(
    resultado.docs
      .map((d) => d.data())
      .filter((d) => d.statusValidacao === "APROVADO")
      .map((d) => d.tipo)
  );

  if (TIPOS_OBRIGATORIOS.every((t) => aprovados.has(t))) {
    await updateDoc(doc(db, "usuarios", dados.motoristaId), { statusMotorista: "ATIVO" });
  }
}

async function rejeitarDocumento(id, dados, motivo) {
  await updateDoc(doc(db, "documentos", id), {
    statusValidacao: "REJEITADO",
    motivoRejeicao: motivo
  });

  // O motorista volta ao envio de documentos
  if (dados.motoristaId && TIPOS_OBRIGATORIOS.includes(dados.tipo)) {
    await updateDoc(doc(db, "usuarios", dados.motoristaId), { statusMotorista: "PENDENTE" });
  }
}

// ---------------------------------------------------------
// Assinaturas
// ---------------------------------------------------------

// Mesma regra do aplicativo (AssinaturaPolicy.estado)
function estadoEfetivo(a, agora) {
  const limite = a.proximaCobranca + CARENCIA_DIAS * DIA_MS;
  switch (a.status) {
    case "PENDENTE": return "Aguardando pagamento";
    case "SUSPENSA": return "Suspensa (suporte)";
    case "CANCELADA": return "Cancelada";
    case "EXPIRADA": return "Expirada";
    case "ATIVA":
      if (agora <= a.proximaCobranca) return a.cancelamentoAgendado ? "Ativa (cancela no fim)" : "Ativa";
      if (a.cancelamentoAgendado) return "Encerrada";
      if (agora <= limite) return "Em carência";
      return "Suspensa por atraso";
    default: return a.status ?? "-";
  }
}

function carregarAssinaturas() {
  onSnapshot(collection(db, "assinaturas"), (snapshot) => {
    listaAssinaturas.innerHTML = "";

    if (snapshot.empty) {
      listaAssinaturas.innerHTML = "<p class='vazio'>Nenhuma assinatura ainda.</p>";
      return;
    }

    const agora = Date.now();
    snapshot.forEach((s) => {
      const a = s.data();
      const card = document.createElement("div");
      card.className = "card-simples";
      card.innerHTML = `
        <div class="info-documento">
          <p><strong>Motorista:</strong> ${esc(s.id)}</p>
          <p><strong>Plano:</strong> ${esc(a.planoNome)} · ${dinheiro(a.valorCentavos)}/mês</p>
          <p><strong>Situação:</strong> ${esc(estadoEfetivo(a, agora))}</p>
          <p><strong>Próxima cobrança:</strong> ${data(a.proximaCobranca)}</p>
        </div>
        <div class="acoes-documento">
          <button class="btn-rejeitar">${a.status === "SUSPENSA" ? "Reativar" : "Suspender"}</button>
          <button class="btn-neutro">Estender 30 dias</button>
        </div>
      `;
      const [btnSuspender, btnEstender] = card.querySelectorAll("button");

      btnSuspender.addEventListener("click", async () => {
        const novo = a.status === "SUSPENSA" ? "ATIVA" : "SUSPENSA";
        if (confirm(novo === "SUSPENSA" ? "Suspender esta assinatura?" : "Reativar esta assinatura?")) {
          await updateDoc(doc(db, "assinaturas", s.id), { status: novo, atualizadoEm: Date.now() });
        }
      });

      btnEstender.addEventListener("click", async () => {
        if (confirm("Estender a vigência em 30 dias?")) {
          const base = Math.max(a.proximaCobranca ?? 0, Date.now());
          await updateDoc(doc(db, "assinaturas", s.id), {
            proximaCobranca: base + 30 * DIA_MS,
            status: a.status === "SUSPENSA" ? "SUSPENSA" : "ATIVA",
            atualizadoEm: Date.now()
          });
        }
      });

      listaAssinaturas.appendChild(card);
    });
  });
}

// ---------------------------------------------------------
// Planos
// ---------------------------------------------------------

document.getElementById("btnPlanosPadrao").addEventListener("click", async () => {
  if (!confirm("Criar (ou restaurar) os planos Básico e Premium?")) return;
  for (const plano of PLANOS_PADRAO) {
    await setDoc(doc(db, "planos", plano.id), plano);
  }
});

function carregarPlanos() {
  onSnapshot(collection(db, "planos"), (snapshot) => {
    listaPlanos.innerHTML = "";

    if (snapshot.empty) {
      listaPlanos.innerHTML =
        "<p class='vazio'>Nenhum plano cadastrado. O aplicativo usa os planos padrão até você criá-los.</p>";
      return;
    }

    const planos = snapshot.docs.map((d) => ({ id: d.id, ...d.data() }))
      .sort((x, y) => (x.ordem ?? 0) - (y.ordem ?? 0));

    planos.forEach((p) => {
      const card = document.createElement("div");
      card.className = "card-simples";
      card.innerHTML = `
        <div class="info-documento">
          <p><strong>${esc(p.nome)}</strong> (${esc(p.id)})</p>
          <p>Valor: ${dinheiro(p.valorCentavos)}/mês</p>
          <p>Limite de rotas: ${p.ilimitado ? "ilimitado" : esc(p.limiteRotas)}</p>
          <p>Situação: ${p.ativo ? "ativo" : "inativo"}</p>
        </div>
        <div class="acoes-documento">
          <button class="btn-neutro">Editar</button>
          <button class="btn-neutro">${p.ativo ? "Desativar" : "Ativar"}</button>
        </div>
      `;
      const [btnEditar, btnAtivo] = card.querySelectorAll("button");

      btnEditar.addEventListener("click", async () => {
        const valor = prompt("Valor mensal em reais (ex.: 99,90):", (p.valorCentavos / 100).toFixed(2).replace(".", ","));
        if (valor === null) return;
        const centavos = Math.round(parseFloat(valor.replace(",", ".")) * 100);
        if (!Number.isFinite(centavos) || centavos <= 0) { alert("Valor inválido."); return; }

        const limite = prompt("Limite de rotas (0 = ilimitado):", p.ilimitado ? "0" : String(p.limiteRotas));
        if (limite === null) return;
        const n = parseInt(limite, 10);
        if (!Number.isInteger(n) || n < 0) { alert("Limite inválido."); return; }

        await updateDoc(doc(db, "planos", p.id), {
          valorCentavos: centavos,
          limiteRotas: n,
          ilimitado: n === 0
        });
      });

      btnAtivo.addEventListener("click", async () => {
        await updateDoc(doc(db, "planos", p.id), { ativo: !p.ativo });
      });

      listaPlanos.appendChild(card);
    });
  });
}