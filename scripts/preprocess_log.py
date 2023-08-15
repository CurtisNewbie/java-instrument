import pystuff

with open('instrumented.log') as f:
    lines = f.readlines()
    prev: int = 1
    indent = {prev: 1}

    for l in lines:
        l = l[:len(l) - 1]
        if l.startswith('Perf'):
            tkn = l.split(",")
            curr = int(tkn[1])
            l = l[6:]

            if not curr in indent:
                indent[curr] = 1

            if prev == curr + 1:
                # print(f"curr: {curr}, prev:{prev}, indent:{indent}")
                indent[curr] = indent[prev] + 1
                prev = curr

            elif prev == curr:
                pass
            else:
                prev = curr

                # print(f"level: {level}, line: {l}")

            l = f'{pystuff.gen_tokens(indent[curr], "  ")}{l}'
            print(f"[PERF] {l}")
        else:
            # pass
            print(l)
