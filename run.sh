#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

mode="${1:-app}"
if [[ $# -gt 1 || ( "$mode" != "app" && "$mode" != "db-test" && "$mode" != "backend" ) ]]; then
  printf 'Usage: %s [app|db-test|backend]\n' "$0" >&2
  exit 2
fi

trim() {
  local value="$1"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s' "$value"
}

load_env_file() {
  local env_file="$PWD/.env"
  [[ -f "$env_file" ]] || return 0

  local line line_number=0 entry key value first last
  local seen_keys='|'
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%$'\r'}"
    ((line_number += 1))
    entry="$(trim "$line")"
    [[ -z "$entry" || "$entry" == \#* ]] && continue

    if [[ ! "$entry" =~ ^([A-Za-z_][A-Za-z0-9_]*)[[:space:]]*=[[:space:]]*(.*)$ ]]; then
      printf 'Malformed .env entry at line %d.\n' "$line_number" >&2
      return 1
    fi
    key="${BASH_REMATCH[1]}"
    value="$(trim "${BASH_REMATCH[2]}")"
    first="${value:0:1}"
    if (( ${#value} > 0 )); then
      last="${value:$((${#value} - 1)):1}"
    else
      last=''
    fi

    if [[ "$first" == '"' || "$first" == "'" || "$last" == '"' || "$last" == "'" ]]; then
      if (( ${#value} < 2 )) || [[ "$first" != "$last" || ( "$first" != '"' && "$first" != "'" ) ]]; then
        printf 'Malformed .env value at line %d.\n' "$line_number" >&2
        return 1
      fi
      value="${value:1:$((${#value} - 2))}"
    fi

    case "$key" in
      SUPABASE_DB_URL|SUPABASE_DB_USER|SUPABASE_DB_PASSWORD|BACKEND_PORT|BACKEND_BIND_ADDRESS|CLYVR_BACKEND_URL|CLYVR_HASH_SIGNER_KEY|CLYVR_HASH_SALT_SEPARATOR|CLYVR_DEMO_DOCTOR_ID_D1|CLYVR_DEMO_DOCTOR_ID_D2|CLYVR_DEMO_DOCTOR_ID_D3|CLYVR_DEMO_DOCTOR_ID_D4)
        if [[ "$seen_keys" == *"|$key|"* ]]; then
          printf 'Duplicate .env variable at line %d.\n' "$line_number" >&2
          return 1
        fi
        seen_keys+="$key|"
        [[ ${!key+x} ]] || export "$key=$value"
        ;;
    esac
  done < "$env_file"
}

load_env_file

if [[ "$mode" == "db-test" ]]; then
  missing=()
  [[ -n "${SUPABASE_DB_URL:-}" ]] || missing+=(SUPABASE_DB_URL)
  [[ -n "${SUPABASE_DB_USER:-}" ]] || missing+=(SUPABASE_DB_USER)
  [[ -n "${SUPABASE_DB_PASSWORD:-}" ]] || missing+=(SUPABASE_DB_PASSWORD)
  if (( ${#missing[@]} > 0 )); then
    printf 'Missing required database environment variable(s): %s.\n' "${missing[*]}" >&2
    exit 1
  fi
fi

if [[ "$mode" == "backend" ]]; then
  command -v mvn >/dev/null 2>&1 || {
    printf 'Maven is required to run the backend.\n' >&2
    exit 1
  }
  required=(SUPABASE_DB_URL SUPABASE_DB_USER SUPABASE_DB_PASSWORD
    CLYVR_DEMO_DOCTOR_ID_D1 CLYVR_DEMO_DOCTOR_ID_D2 CLYVR_DEMO_DOCTOR_ID_D3 CLYVR_DEMO_DOCTOR_ID_D4
    CLYVR_HASH_SIGNER_KEY CLYVR_HASH_SALT_SEPARATOR)
  missing=()
  for key in "${required[@]}"; do
    [[ -n "${!key:-}" ]] || missing+=("$key")
  done
  if (( ${#missing[@]} > 0 )); then
    printf 'Missing required local backend environment variable(s): %s.\n' "${missing[*]}" >&2
    exit 1
  fi
  exec mvn -f backend/pom.xml spring-boot:run
fi

mkdir -p out
find src -name '*.java' -print0 | xargs -0 javac -encoding UTF-8 -d out

if [[ "$mode" == "db-test" ]]; then
  main_class='clyvr.database.DatabaseConnectionTest'
else
  main_class='clyvr.controller.AppController'
fi
if [[ "$mode" == "app" ]]; then
  unset SUPABASE_DB_URL SUPABASE_DB_USER SUPABASE_DB_PASSWORD
  unset CLYVR_DEMO_DOCTOR_ID_D1 CLYVR_DEMO_DOCTOR_ID_D2 CLYVR_DEMO_DOCTOR_ID_D3 CLYVR_DEMO_DOCTOR_ID_D4
  unset CLYVR_HASH_SIGNER_KEY CLYVR_HASH_SALT_SEPARATOR
fi
java -cp 'out:lib/postgresql-42.7.14.jar' "$main_class"
