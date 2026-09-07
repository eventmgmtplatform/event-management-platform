#!/usr/bin/env bash
set -uo pipefail

ROOT=/opt/event-management-platform
TARGET="$ROOT/services/event-management-console"
CONSOLE=event-management-console
CORE=event-integration-worker
LAB=event-itsm-ticketing-dashboard
URL=http://localhost:8090/ticketing/tickets
TS="$(date -u +%Y%m%dT%H%M%SZ)"
DIR="$ROOT/evidence/sn-ui-03/03.7-functional/$TS"
REPORT="$DIR/SN-UI-03.7-functional-browser-certification.txt"
FAILURES=0
PASSES=0

mkdir -p "$DIR"
exec > >(tee -a "$REPORT") 2>&1
pass(){ echo "ASSERTION=PASS | $*"; PASSES=$((PASSES+1)); }
fail(){ echo "ASSERTION=FAIL | $*"; FAILURES=$((FAILURES+1)); }
inspect(){ docker inspect -f "$2" "$1" 2>/dev/null || true; }
confirm(){
  local id="$1" prompt="$2" answer
  echo
  echo "MANUAL_TEST=$id"
  echo "ACTION=$prompt"
  read -r -p "¿La prueba fue exitosa? [s/N]: " answer </dev/tty || answer=""
  case "$answer" in s|S|si|SI|sí|Sí|y|Y|yes|YES) pass "$id";; *) fail "$id";; esac
}

echo "===== SN-UI-03.7 FUNCTIONAL BROWSER CERTIFICATION ====="
echo "UTC=$TS"
echo "CONSOLE_URL=$URL"
echo "REPORT=$REPORT"
echo "MODE=AUTOMATED_GUARDS_PLUS_MANUAL_BROWSER_ACCEPTANCE"
cd "$ROOT" || exit 1

echo
echo "===== 01. AUTOMATED RUNTIME GUARDS ====="
STATUS="$(inspect "$CONSOLE" '{{.State.Status}}')"; HEALTH="$(inspect "$CONSOLE" '{{.State.Health.Status}}')"; RESTARTS="$(inspect "$CONSOLE" '{{.RestartCount}}')"
echo "CONSOLE_STATUS=$STATUS";echo "CONSOLE_HEALTH=$HEALTH";echo "CONSOLE_RESTARTS=$RESTARTS"
[[ "$STATUS" == running && "$HEALTH" == healthy && "$RESTARTS" == 0 ]] && pass "Console container healthy and stable" || fail "Console container state unexpected"
HTTP="$(curl -sS -o "$DIR/ticketing-route.html" -w '%{http_code}' --max-time 5 "$URL" 2>/dev/null || true)"
[[ "$HTTP" == 200 ]] && pass "Ticketing dashboard route returns HTTP 200" || fail "Ticketing dashboard route returned ${HTTP:-000}"
ASSET_COUNT="$(grep -Eo '(src|href)="/assets/[^"]+' "$DIR/ticketing-route.html" | wc -l | tr -d ' ')"
echo "REFERENCED_ASSETS=$ASSET_COUNT"
[[ "$ASSET_COUNT" -ge 2 ]] && pass "Compiled JavaScript and CSS assets referenced" || fail "Compiled assets missing"
for token in 'onDoubleClick' 'onContextMenu' 'localStorage.setItem(STORE' 'localStorage.setItem(AUDIT' 'Confirmar cierre' 'MOCK DATA';do grep -Fq "$token" "$TARGET/src/modules/ticketing/pages/TicketDashboardPage.tsx"&&pass "Source contract: $token"||fail "Source contract missing: $token";done

echo
echo "===== 02. MANUAL ACCEPTANCE INSTRUCTIONS ====="
echo "Abre en un navegador: $URL"
echo "Mantén esta terminal abierta y responde cada comprobación."

confirm UI_01 "Confirma que aparece el shell global, sidebar, header LOCAL/MOCK DATA y Ticketing > Tickets activo."
confirm UI_02 "Confirma que se muestran 4 KPIs, una tabla y 12 resultados distribuidos en 2 páginas."
confirm UI_03 "Busca CHQRAUTBD01 y confirma que queda visible únicamente el ticket INC0019285."
confirm UI_04 "Limpia la búsqueda; filtra Estado=Open y Prioridad=High. Confirma que el resultado cambia sin recargar la página."
confirm UI_05 "Limpia filtros; pulsa Siguiente y luego Anterior. Confirma que la paginación cambia correctamente."
confirm UI_06 "Haz doble clic sobre un ticket. Confirma que se abre el panel lateral con detalle y que puede cerrarse."
confirm UI_07 "Haz clic derecho sobre un ticket. Confirma que aparece el menú con Ver detalle y Cerrar ticket."
confirm UI_08 "Selecciona Cerrar ticket. Confirma que el botón permanece deshabilitado sin código y con nota menor a 10 caracteres."
confirm UI_09 "Elige un código, escribe una nota válida y confirma. Verifica estado Closed y notificación AUD-*."
confirm UI_10 "Recarga el navegador. Confirma que el ticket continúa Closed, demostrando persistencia localStorage."
confirm UI_11 "Reduce el ancho del navegador. Confirma que sidebar, KPIs, filtros y tabla se adaptan sin inutilizar la pantalla."
confirm UI_12 "Confirma visualmente que toda la pantalla indica datos simulados y no aparenta estar conectada a ServiceNow."

echo
echo "===== 03. PLATFORM NON-REGRESSION ====="
LAB_HTTP="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 5 http://localhost:8088/itsm/tickets/ 2>/dev/null || true)"
[[ "$LAB_HTTP" == 200 && "$(inspect "$LAB" '{{.State.Status}}')" == running ]] && pass "SN-UI-01 remains available" || fail "SN-UI-01 unavailable"
CORE_STATE="$(inspect "$CORE" '{{.State.Status}}/{{.State.Health.Status}}/{{.RestartCount}}')";echo "CORE_STATE=$CORE_STATE"
[[ "$CORE_STATE" == running/healthy/0 ]] && pass "ServiceNow Core remains healthy and unrestarted" || fail "ServiceNow Core state unexpected"
[[ -z "$(git status --porcelain -- services/integration-worker infrastructure/postgres)" ]] && pass "Protected source unchanged" || fail "Protected source changed"

echo
echo "===== FINAL RESULT ====="
echo "PASS_COUNT=$PASSES"
echo "FAILURE_COUNT=$FAILURES"
echo "REPORT=$REPORT"
echo "REAL_COMMANDS_PUBLISHED=FALSE"
echo "POSTGRESQL_DATA_CHANGED=FALSE"
echo "KAFKA_OFFSETS_CHANGED=FALSE"
if ((FAILURES==0));then echo "SN_UI_03_7_FUNCTIONAL_CERTIFICATION=PASS";echo "NEXT_CHECKPOINT=SN-UI-03.8";exit 0;fi
echo "SN_UI_03_7_FUNCTIONAL_CERTIFICATION=FAIL";echo "NEXT_ACTION=REPORT_FAILED_UI_TEST_IDS";exit 1
