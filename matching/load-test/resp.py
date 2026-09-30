"""의존성 없는 최소 Redis RESP 클라이언트. 폴링 오버헤드를 줄이려고
docker exec redis-cli 대신 호스트 6379 로 직접 붙는다."""
import socket


class Resp:
    def __init__(self, host="127.0.0.1", port=6379, timeout=10):
        self.s = socket.create_connection((host, port), timeout=timeout)
        self.s.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        self.f = self.s.makefile("rb")

    def send(self, *args):
        out = [b"*%d\r\n" % len(args)]
        for a in args:
            b = a.encode() if isinstance(a, str) else a
            out.append(b"$%d\r\n" % len(b))
            out.append(b)
            out.append(b"\r\n")
        self.s.sendall(b"".join(out))

    def read(self):
        line = self.f.readline()
        if not line:
            raise ConnectionError("redis closed")
        t, rest = line[0:1], line[:-2][1:]
        if t == b"$":
            n = int(rest)
            if n == -1:
                return None
            return self.f.read(n + 2)[:-2]
        if t == b"+":
            return rest
        if t == b":":
            return int(rest)
        if t == b"-":
            raise RuntimeError(rest.decode())
        if t == b"*":
            n = int(rest)
            if n == -1:
                return None
            return [self.read() for _ in range(n)]
        raise RuntimeError("bad type %r" % t)

    def cmd(self, *args):
        self.send(*args)
        return self.read()

    def close(self):
        try:
            self.s.close()
        except Exception:
            pass
