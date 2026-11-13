import socket,struct,sys
def pkt(i,t,b): d=struct.pack('<ii',i,t)+b.encode()+b'\0\0'; return struct.pack('<i',len(d))+d
s=socket.create_connection(('127.0.0.1',int(sys.argv[1])),timeout=20); s.sendall(pkt(1,3,'crazyphone-dev')); s.recv(4096)
for c in sys.argv[2:]:
    s.sendall(pkt(2,2,c)); r=s.recv(65536); print(c,'->',r[12:-2].decode(errors='replace'))
