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
const listaHistorico = document.getElementById("listaHistorico");
const campoBusca = document.getElementById("campoBusca");
const avisoNovo = document.getElementById("avisoNovo");
const badgeDocumentos = document.getElementById("badgeDocumentos");
const btnSair = document.getElementById("btnSair");

// Documentos que o motorista precisa ter APROVADOS para ser liberado.
// Mesmos tipos gravados pelo aplicativo (DocumentoRepository.TIPO_*).
// A CNH é um único documento: frente e verso (ou PDF, na CNH digital).
const TIPOS_OBRIGATORIOS = ["CNH", "COMPROVANTE_RESIDENCIA", "SELFIE"];

const ROTULOS_TIPO = {
  CNH: "CNH",
  COMPROVANTE_RESIDENCIA: "Comprovante de residência",
  SELFIE: "Foto do motorista"
};

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

// Identifica PDF pelo nome do arquivo na URL do Storage (ignora o "?token=...")
function ehPdf(url) {
  try {
    return decodeURIComponent(new URL(url).pathname).toLowerCase().endsWith(".pdf");
  } catch (e) {
    return false;
  }
}

// Imagem clicável (abre em nova aba) ou link, quando o arquivo é PDF
function htmlDoArquivo(url, legenda) {
  const segura = urlSegura(url);
  if (!segura) return "";
  if (ehPdf(segura)) {
    return `<p><a href="${esc(segura)}" target="_blank" rel="noopener">Abrir PDF${legenda ? " (" + esc(legenda) + ")" : ""}</a></p>`;
  }
  return `<a href="${esc(segura)}" target="_blank" rel="noopener"><img src="${esc(segura)}" alt="${esc(legenda || "Documento")}" class="img-documento" /></a>`;
}

function dinheiro(centavos) {
  return (Number(centavos || 0) / 100).toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
}

function data(ms) {
  return ms ? new Date(ms).toLocaleDateString("pt-BR") : "-";
}

function dataHora(ms) {
  return ms ? new Date(ms).toLocaleString("pt-BR") : "-";
}

// Dados do motorista (nome, telefone, e-mail) a partir do UID, com cache
const cacheUsuarios = new Map();

function buscarUsuario(uid) {
  if (!uid) return Promise.resolve({});
  if (!cacheUsuarios.has(uid)) {
    cacheUsuarios.set(
      uid,
      getDoc(doc(db, "usuarios", uid))
        .then((s) => (s.exists() ? s.data() : {}))
        .catch(() => ({}))
    );
  }
  return cacheUsuarios.get(uid);
}

function nomeDe(u, uid) {
  return u.nome || `Motorista (${String(uid ?? "").slice(0, 6)}…)`;
}

// Busca: esconde os itens cujo texto (data-busca) não contém o termo digitado
function aplicarBusca() {
  const termo = campoBusca.value.trim().toLowerCase();
  document.querySelectorAll(".item-busca").forEach((el) => {
    el.hidden = termo !== "" && !(el.dataset.busca ?? "").includes(termo);
  });
}

campoBusca.addEventListener("input", aplicarBusca);

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
  carregarHistorico();
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
    document.getElementById("abaHistorico").hidden = botao.dataset.aba !== "historico";
    document.getElementById("abaAssinaturas").hidden = botao.dataset.aba !== "assinaturas";
    document.getElementById("abaPlanos").hidden = botao.dataset.aba !== "planos";
  });
});

// ---------------------------------------------------------
// Documentos
// ---------------------------------------------------------

let primeiraCargaDocumentos = true;
let renderizacao = 0;
const idsNovos = new Set();

function carregarDocumentosPendentes() {
  const consulta = query(collection(db, "documentos"), where("statusValidacao", "==", "EM_ANALISE"));

  onSnapshot(consulta, async (snapshot) => {

    // Alerta de novos envios (não dispara na primeira carga da página)
    if (!primeiraCargaDocumentos) {
      const chegaram = snapshot.docChanges().filter((c) => c.type === "added");
      if (chegaram.length > 0) {
        chegaram.forEach((c) => idsNovos.add(c.doc.id));
        mostrarAvisoNovo(chegaram.length);
      }
    }
    primeiraCargaDocumentos = false;

    atualizarContadorPendentes(snapshot.size);

    const minha = ++renderizacao;

    if (snapshot.empty) {
      listaDocumentos.innerHTML = "<p class='vazio'>Nenhum documento pendente no momento.</p>";
      return;
    }

    // Agrupa os documentos por motorista
    const grupos = new Map();
    snapshot.forEach((d) => {
      const dados = d.data();
      const chave = dados.motoristaId ?? "sem-motorista";
      if (!grupos.has(chave)) grupos.set(chave, []);
      grupos.get(chave).push({ id: d.id, dados });
    });

    const cartoes = [];
    for (const [uid, docs] of grupos) {
      const usuario = await buscarUsuario(uid === "sem-motorista" ? null : uid);
      cartoes.push(criarGrupoMotorista(uid, usuario, docs));
    }

    // Se chegou outra atualização enquanto buscava os nomes, descarta esta
    if (minha !== renderizacao) return;

    listaDocumentos.innerHTML = "";
    cartoes.forEach((c) => listaDocumentos.appendChild(c));
    aplicarBusca();
  });
}

function atualizarContadorPendentes(total) {
  badgeDocumentos.textContent = total;
  badgeDocumentos.hidden = total === 0;
  document.title = total > 0 ? `(${total}) VaiVan - Painel Admin` : "VaiVan - Painel Admin";
}

function mostrarAvisoNovo(qtd) {
  avisoNovo.textContent = qtd === 1
    ? "Novo documento recebido para análise."
    : `${qtd} novos documentos recebidos para análise.`;
  avisoNovo.hidden = false;
  clearTimeout(mostrarAvisoNovo.timer);
  mostrarAvisoNovo.timer = setTimeout(() => { avisoNovo.hidden = true; }, 8000);
}

avisoNovo.addEventListener("click", () => { avisoNovo.hidden = true; });

function criarGrupoMotorista(uid, usuario, docs) {
  const grupo = document.createElement("div");
  grupo.className = "grupo-motorista item-busca";
  grupo.dataset.busca = [usuario.nome, usuario.telefone, usuario.email, usuario.cpf, uid]
    .filter(Boolean).join(" ").toLowerCase();

  const total = docs.length;
  grupo.innerHTML = `
    <div class="cabecalho-motorista">
      <p class="nome-motorista">${esc(nomeDe(usuario, uid))}</p>
      <p>${esc(usuario.telefone || "Telefone não informado")}${usuario.email ? " · " + esc(usuario.email) : ""}</p>
      <p class="resumo-motorista">${total} documento${total > 1 ? "s" : ""} em análise</p>
    </div>
  `;

  docs.forEach(({ id, dados }) => {
    const card = criarCardDocumento(id, dados);
    if (idsNovos.has(id)) card.classList.add("novo");
    grupo.appendChild(card);
  });

  return grupo;
}

function criarCardDocumento(id, dados) {
  const card = document.createElement("div");
  card.className = "card-documento";

  const rotulo = ROTULOS_TIPO[dados.tipo] ?? dados.tipo ?? "Não informado";
  const modalidade = dados.modalidadeCnh === "DIGITAL" ? " (digital)" : "";

  // CNH física tem frente e verso; os demais têm um arquivo só
  const imagens = dados.arquivoVerso
    ? htmlDoArquivo(dados.arquivo, "frente") + htmlDoArquivo(dados.arquivoVerso, "verso")
    : htmlDoArquivo(dados.arquivo, "");

  card.innerHTML = `
    ${imagens}
    <div class="info-documento">
      <p><strong>Tipo:</strong> ${esc(rotulo)}${esc(modalidade)}</p>
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

// ---------------------------------------------------------
// Histórico de análises
// ---------------------------------------------------------

function carregarHistorico() {
  const consulta = query(
    collection(db, "documentos"),
    where("statusValidacao", "in", ["APROVADO", "REJEITADO"])
  );

  onSnapshot(consulta, async (snapshot) => {
    const minha = ++renderizacaoHistorico;

    if (snapshot.empty) {
      listaHistorico.innerHTML = "<p class='vazio'>Nenhuma análise realizada ainda.</p>";
      return;
    }

    // Mais recentes primeiro (ordena aqui para não exigir índice no Firestore)
    const itens = snapshot.docs
      .map((d) => ({ id: d.id, ...d.data() }))
      .sort((x, y) => (y.analisadoEm ?? 0) - (x.analisadoEm ?? 0))
      .slice(0, 100);

    const linhas = [];
    for (const item of itens) {
      const usuario = await buscarUsuario(item.motoristaId);
      const rotulo = ROTULOS_TIPO[item.tipo] ?? item.tipo ?? "Documento";
      const aprovado = item.statusValidacao === "APROVADO";

      const linha = document.createElement("div");
      linha.className = "card-simples item-busca";
      linha.dataset.busca = [usuario.nome, usuario.telefone, usuario.email, rotulo]
        .filter(Boolean).join(" ").toLowerCase();
      linha.innerHTML = `
        <div class="info-documento">
          <p><strong>${esc(nomeDe(usuario, item.motoristaId))}</strong> · ${esc(rotulo)}</p>
          <p>${esc(usuario.telefone || "")}</p>
          <p>Analisado em: ${esc(dataHora(item.analisadoEm))}</p>
          ${!aprovado && item.motivoRejeicao ? `<p class="motivo">Motivo: ${esc(item.motivoRejeicao)}</p>` : ""}
        </div>
        <span class="selo ${aprovado ? "selo-ok" : "selo-erro"}">${aprovado ? "Aprovado" : "Rejeitado"}</span>
      `;
      linhas.push(linha);
    }

    if (minha !== renderizacaoHistorico) return;

    listaHistorico.innerHTML = "";
    linhas.forEach((l) => listaHistorico.appendChild(l));
    aplicarBusca();
  });
}

let renderizacaoHistorico = 0;

async function aprovarDocumento(id, dados) {
  await updateDoc(doc(db, "documentos", id), {
    statusValidacao: "APROVADO",
    motivoRejeicao: null,
    analisadoEm: Date.now(),
    analisadoPor: auth.currentUser?.uid ?? null
  });

  // Documento de veículo ou sem motorista: nada a liberar
  if (!dados.motoristaId || !TIPOS_OBRIGATORIOS.includes(dados.tipo)) return;

  // Se os documentos obrigatórios estiverem todos aprovados, libera o motorista
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
    motivoRejeicao: motivo,
    analisadoEm: Date.now(),
    analisadoPor: auth.currentUser?.uid ?? null
  });

  // O motorista volta à situação "pendente" até reenviar o documento
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
  onSnapshot(collection(db, "assinaturas"), async (snapshot) => {
    const minha = ++renderizacaoAssinaturas;

    if (snapshot.empty) {
      listaAssinaturas.innerHTML = "<p class='vazio'>Nenhuma assinatura ainda.</p>";
      return;
    }

    const agora = Date.now();
    const cartoes = [];
    for (const s of snapshot.docs) {
      const a = s.data();
      const usuario = await buscarUsuario(s.id);
      const card = document.createElement("div");
      card.className = "card-simples item-busca";
      card.dataset.busca = [usuario.nome, usuario.telefone, usuario.email, a.planoNome]
        .filter(Boolean).join(" ").toLowerCase();
      card.innerHTML = `
        <div class="info-documento">
          <p><strong>Motorista:</strong> ${esc(nomeDe(usuario, s.id))}</p>
          <p>${esc(usuario.telefone || "")}</p>
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

      cartoes.push(card);
    }

    if (minha !== renderizacaoAssinaturas) return;

    listaAssinaturas.innerHTML = "";
    cartoes.forEach((c) => listaAssinaturas.appendChild(c));
    aplicarBusca();
  });
}

let renderizacaoAssinaturas = 0;

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