"""Optional grammar check; no database execution or RLS effectiveness claim."""
from pathlib import Path
import re

from pglast import parse_plpgsql, parse_sql


root = Path(__file__).parent
statement_count = 0
body_count = 0
for file in sorted(root.rglob("*.sql")):
    source = file.read_text(encoding="utf-8")
    count = len(parse_sql(source))
    statement_count += count
    print(f"{file.relative_to(root)}: {count} SQL statements parsed")
    for statement in re.findall(r"create function .*?\$\$;", source, re.I | re.S):
        if "language plpgsql" in statement:
            parse_plpgsql(statement)
            body_count += 1
        else:
            # SQL-language function bodies are text literals in CREATE FUNCTION.
            body = statement.split("$$", 2)[1]
            parse_sql(body)
            body_count += 1
print(f"PASS: {statement_count} top-level statements, {body_count} function bodies parsed.")
print("Database object resolution, RLS behavior and pgTAP execution remain unverified.")
